//
//  Bidders.swift
//  DynamicPrice
//
//  Created by Jason Sznol on 4/7/25.
//

@preconcurrency import NimbusGAMKit
@preconcurrency import DTBiOSSDK
import GoogleMobileAds

public protocol Bidder: Sendable {
    func fetchBid() async throws -> Bid
}

public enum Bid: Sendable {
    case nimbus(NimbusAd)
    case aps(APSAd)
}

public extension Collection where Element == any Bidder  {
    func auction() async -> [Bid] {
        await withTaskGroup(of: Optional<Bid>.self, returning: [Bid].self) { group in
            for bidder in self {
                group.addTask {
                    try? await bidder.fetchBid()
                }
            }
            
            var bids: [Bid] = []
            for await bid in group {
                if let bid = bid {
                    bids.append(bid)
                }
            }
            return bids
        }
    }
}

extension Bid {

    func applyTargeting(to request: GAMRequest, priceMapping: NimbusGAMLinearPriceMapping) {
        switch self {
        case let .nimbus(response):
            response.applyDynamicPrice(into: request, mapping: priceMapping)
        case let .aps(response):
            response.customTargeting?.forEach {
                request.customTargeting?[$0.key] = $0.value
            }
        }
    }
}

public final class NimbusBidder: Bidder {
    class RequestListener : NimbusRequestManagerDelegate {

        private let continuation: UnsafeContinuation<Bid, Error>

        public init(_ continuation: UnsafeContinuation<Bid, Error>) {
            self.continuation = continuation
        }

        func didCompleteNimbusRequest(request: NimbusRequest, ad: NimbusAd) {
            continuation.resume(returning: .nimbus(ad))
        }

        func didFailNimbusRequest(request: NimbusRequest, error: NimbusError) {
            continuation.resume(throwing: error)
        }
    }

    private let provider: @Sendable () -> NimbusRequest
    private let requestManager = NimbusRequestManager()
    
    public init(_ request: @autoclosure @escaping @Sendable () -> NimbusRequest) {
        provider = request
    }
    
    public func fetchBid() async throws -> Bid {
        var listener: RequestListener?
        let result = try await withUnsafeThrowingContinuation { continuation in
            listener = RequestListener(continuation)
            requestManager.delegate = listener
            requestManager.performRequest(request: provider())
        }
        return result
    }
}

public final class APSBidder: Bidder {
    let provider: @Sendable () -> APSAdRequest

    public init(_ request: @autoclosure @escaping @Sendable () -> APSAdRequest) {
        provider = request
    }
    
    public func fetchBid() async throws -> Bid {
        let request = provider()
        let result: Bid = try await withUnsafeThrowingContinuation { continuation in
            request.loadAd { adResponse, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                
                continuation.resume(returning: .aps(adResponse))
            }
        }
        return result
    }
}

public extension NimbusRequest {
    @inlinable
    func asBidder() -> NimbusBidder { NimbusBidder(self) }
}

public extension APSAdRequest {
    @inlinable
    func asBidder() -> APSBidder { APSBidder(self) }
}
