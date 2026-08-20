//
//  Bidders.swift
//  DynamicPrice
//
//  Created by Jason Sznol on 4/7/25.
//

@preconcurrency import DTBiOSSDK
import DynamicPrice
import GoogleMobileAds
import NimbusKit

public protocol Bidder: Sendable {
    func fetchBid() async throws -> Bid
}

public enum Bid: Sendable {
    case nimbus(NimbusResponse)
    case aps(APSAd)
    case test
}

public enum AuctionError: Error {
    case timeout
}

extension Collection where Element == any Bidder {

    public func auction(timeout: Duration = .milliseconds(3000)) async -> [Bid] {
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
            for _ in 0..<self.count {
                guard let result = await group.nextResult() else { break }
                switch result {
                case .success(let bid): bids.append(bid)
                case .failure(let error):
                    if case AuctionError.timeout = error {
                        group.cancelAll()
                        break
                    }
                }
            }
            return bids
        }
    }
}

extension Bid {

    public func applyTargeting(to request: AdManagerRequest, priceMapping: LinearPriceMapping) {
        switch self {
        case .nimbus(let response):
            response.applyDynamicPrice(request, mapping: priceMapping)
        case .aps(let response):
            response.customTargeting?.forEach {
                request.customTargeting?[$0.key] = $0.value
            }
        case _: return
        }
    }
}

public final class NimbusBidder: Bidder {

    private let provider: @Sendable () -> Ad

    public init(_ request: @autoclosure @escaping @Sendable () -> Ad) {
        provider = request
    }

    public func fetchBid() async throws -> Bid {
        let request = provider()
        let response = try await withTaskCancellationHandler {
            try await request.fetch().response!
        } onCancel: {

        }
        return .nimbus(response)
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

extension Ad {
    @inlinable
    public func asBidder() -> NimbusBidder { NimbusBidder(self) }
}

extension APSAdRequest {
    @inlinable
    public func asBidder() -> APSBidder { APSBidder(self) }
}
