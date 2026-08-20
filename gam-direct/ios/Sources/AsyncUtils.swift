//
//  Nimbus+Async.swift
//  GAMDirect
//
//  Created by Jason Sznol on 5/7/26.
//  Copyright © 2026 AdsByNimbus. All rights reserved.
//

@preconcurrency import DTBiOSSDK
import NimbusKit

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

extension Task where Success == Never, Failure == Never {
    @inlinable
    static func sleep(seconds: TimeInterval) async {
        if seconds > 0 {
            try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
        }
    }
}

