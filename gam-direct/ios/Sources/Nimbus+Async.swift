//
//  Nimbus+Async.swift
//  GAMDirect
//
//  Created by Jason Sznol on 5/7/26.
//  Copyright © 2026 AdsByNimbus. All rights reserved.
//

@preconcurrency import DTBiOSSDK
@preconcurrency import NimbusKit

public extension APSAdRequest {
    @MainActor
    func loadAd() async throws -> APSAd {
        nonisolated(unsafe) var continuation: UnsafeContinuation<APSAd, Error>?
        let response: APSAd = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { c in
                continuation = c
                loadAd { adResponse, error in
                    if let error = error {
                        continuation?.resume(throwing: error)
                    } else {
                        continuation?.resume(returning: adResponse)
                    }
                    continuation = nil
                }
            }
        } onCancel: {
            continuation?.resume(throwing: CancellationError())
            continuation = nil
        }
        return response
    }
}

extension NimbusRequest {
    // Helper method for appending APS params using the new APSAd methods
    public func addAPSResponse(_ response: APSAd) {
        guard let targeting = response.customTargeting else { return }

        var ext = impressions[0].extensions ?? [:]
        ext["aps"] = NimbusCodable([targeting])
        impressions[0].extensions = ext
    }
    
    final class RequestListener : NimbusRequestManagerDelegate, Sendable {

        nonisolated(unsafe) var continuation: UnsafeContinuation<NimbusAd, Error>?

        func didCompleteNimbusRequest(request: NimbusRequest, ad: NimbusAd) {
            continuation?.resume(returning: ad)
            continuation = nil
        }

        func didFailNimbusRequest(request: NimbusRequest, error: NimbusError) {
            continuation?.resume(throwing: error)
            continuation = nil
        }
    }

    public func fetchAd() async throws -> NimbusAd {
        let requestManager = NimbusRequestManager()
        let listener = RequestListener()
        requestManager.delegate = listener
        let bid = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { continuation in
                listener.continuation = continuation
                requestManager.performRequest(request: self)
            }
        } onCancel: {
            listener.continuation?.resume(throwing: CancellationError())
            listener.continuation = nil
        }
        return bid
    }
}

extension Task where Success == Never, Failure == Never {
    @inlinable
    static func sleep(seconds: TimeInterval) async {
        if seconds > 0 {
            try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
        }
    }
}

