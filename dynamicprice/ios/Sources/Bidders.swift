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
    case test
}

public enum AuctionError : Error {
    case timeout
}

public extension Collection where Element == any Bidder  {

    func auction(timeout: Duration = .milliseconds(3000)) async -> [Bid] {
        await withThrowingTaskGroup(of: Bid.self, returning: [Bid].self) { group in
            for bidder in self {
                group.addTask { try await bidder.fetchBid() }
            }

            group.addTask {
                try await Task.sleep(until: .now + timeout)
                try Task.checkCancellation()
                throw AuctionError.timeout
            }
            
            defer { group.cancelAll() }

            var bids: [Bid] = []
            for _ in 0 ..< self.count {
                guard let result = await group.nextResult() else { break }
                switch result {
                    case .success(let bid): bids.append(bid)
                    case .failure(let error): if case AuctionError.timeout = error {
                        group.cancelAll()
                        break
                    }
                }
            }
            return bids
        }
    }
}

public extension Bid {

    func applyTargeting(to request: AdManagerRequest, priceMapping: NimbusGAMLinearPriceMapping) {
        switch self {
        case let .nimbus(response):
            response.applyDynamicPrice(into: request, mapping: priceMapping)
        case let .aps(response):
            response.customTargeting?.forEach {
                request.customTargeting?[$0.key] = $0.value
            }
        case _: return
        }
    }
}

public final class NimbusBidder: Bidder {
    
    final class RequestListener : NimbusRequestManagerDelegate, Sendable {

        nonisolated(unsafe) var continuation: UnsafeContinuation<Bid, Error>?

        func didCompleteNimbusRequest(request: NimbusRequest, ad: NimbusAd) {
            continuation?.resume(returning: .nimbus(ad))
            continuation = nil
        }

        func didFailNimbusRequest(request: NimbusRequest, error: NimbusError) {
            continuation?.resume(throwing: error)
            continuation = nil
        }
    }

    private let provider: @Sendable () -> NimbusRequest

    public init(_ request: @autoclosure @escaping @Sendable () -> NimbusRequest) {
        provider = request
    }

    public func fetchBid() async throws -> Bid {
        let request = provider()
        let requestManager = NimbusRequestManager()
        let listener = RequestListener()
        requestManager.delegate = listener
        let bid = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { continuation in
                listener.continuation = continuation
                requestManager.performRequest(request: request)
            }
        } onCancel: {
            listener.continuation?.resume(throwing: CancellationError())
            listener.continuation = nil
        }
        return bid
    }
}

public final class APSBidder: Bidder {
    let provider: @Sendable () -> APSAdRequest

    public init(_ request: @autoclosure @escaping @Sendable () -> APSAdRequest) {
        provider = request
    }

    public func fetchBid() async throws -> Bid {
        let request = provider()
        nonisolated(unsafe) var continuation: UnsafeContinuation<Bid, Error>?
        let bid: Bid = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { c in
                continuation = c
                request.loadAd { adResponse, error in
                    if let error = error {
                        continuation?.resume(throwing: error)
                    } else {
                        continuation?.resume(returning: .aps(adResponse))
                    }
                    continuation = nil
                }
            }
        } onCancel: {
            continuation?.resume(throwing: CancellationError())
            continuation = nil
        }
        return bid
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
