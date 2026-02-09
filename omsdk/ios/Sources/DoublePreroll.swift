//
//  DoublePreroll.swift
//
@preconcurrency import NimbusKit
import SwiftUI

extension UIViewController {
    func doublePreroll(
        container: UIView,
        loadTimeout: Duration = .seconds(3),
        requestManager: NimbusRequestManager = NimbusRequestManager(),
        onVideoResponse: @escaping (Int, NimbusAd) -> Void = { _, _ in },
    ) async throws {
        var currentVideo: AdController? = nil
        defer { currentVideo?.destroy() }

        let request = NimbusRequest.forVideoAd(position: "preroll")
        request.impressions[0].video?.maxDuration = 15
        request.impressions[0].video?.placementType = .inStream
        request.impressions[0].video?.startDelay = -1

        // Request and render the first pre-roll video; playback will start
        // once the View is at least 25% visible on screen.
        let firstBid = try await requestManager.fetch(request: request)
        onVideoResponse(0, firstBid)
        currentVideo = await MainActor.run {
            Nimbus.load(
                ad: firstBid,
                container: container,
                adPresentingViewController: self,
                delegate: nil
            )
        }
        
        // As a safeguard we add a timeout to ensure the LOADED event fires
        _ = try await currentVideo?.waitFor(event: .loaded, timeout: loadTimeout)
        
        // Next we want to wait for the video to progress before requesting the
        // next ad; the third quartile will leave ~3-4 seconds for the next video to load
        _ = try await currentVideo?.waitFor(event: .thirdQuartile)
        
        // Start the request for the 2nd video async but do not block
        async let secondRequest = try requestManager.fetch(request: request)
        
        _ = try await currentVideo?.waitFor(event: .completed)
        
        // The request will already be finished when we call await
        let secondBid = try await secondRequest
        // The passback of the response differs from the Kotlin implementation
        // because Swift complains about data races
        onVideoResponse(1, secondBid)
        
        currentVideo = await MainActor.run {
            currentVideo?.destroy()
            return Nimbus.load(
                ad: secondBid,
                container: container,
                adPresentingViewController: self,
                delegate: nil
            )
        }
        
        _ = try await currentVideo?.waitFor(event: .impression, timeout: loadTimeout)
        
        _ = try await currentVideo?.waitFor(event: .completed)
    }
}

@MainActor
struct DoublePrerollView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> some UIViewController {
        let vc = UIViewController()

        Task {
            try? await vc.doublePreroll(container: vc.view) { index, response in
                print("DoublePreroll[\(index)]: \(response.bidRaw)")
            }
            // The two prerolls have completed at this point
        }

        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {}
}


extension AdController {

    func waitFor(
        event: NimbusEvent,
        timeout: Duration = .zero,
    ) async throws-> AdController {
        let existingDelegate = delegate
        defer { delegate = existingDelegate }
        let listener = WaitForListener(event: event)
        delegate = listener
        return try await withThrowingTaskGroup(
            of: AdController.self,
            returning: AdController.self
        ) { group in
            group.addTask {
                return try await withTaskCancellationHandler {
                    try await withUnsafeThrowingContinuation { continuation in
                        listener.continuation = continuation
                    }
                } onCancel: {
                    listener.continuation?.resume(throwing: CancellationError())
                    listener.continuation = nil
                }
            }

            if timeout > .zero {
                group.addTask {
                    try await Task.sleep(until: .now + timeout)
                    try Task.checkCancellation()
                    throw CancellationError()
                }
            }

            defer { group.cancelAll() }

            switch await group.nextResult() {
            case .success(let controller): return controller
            default: throw CancellationError()
            }
        }
    }
}

final class WaitForListener: AdControllerDelegate, Sendable {
    nonisolated(unsafe) var continuation:
        UnsafeContinuation<AdController, Error>?

    let target: NimbusEvent

    init(event: NimbusEvent) {
        target = event
    }

    func didReceiveNimbusEvent(controller: any AdController, event: NimbusEvent)
    {
        guard event == target else {
            if event == .destroyed {
                continuation?.resume(throwing: CancellationError())
                continuation = nil
            }
            return
        }
        continuation?.resume(returning: controller)
        continuation = nil
    }

    func didReceiveNimbusError(
        controller: any AdController,
        error: any NimbusError
    ) {
        continuation?.resume(throwing: error)
        continuation = nil
    }
}

extension NimbusRequestManager {

    final class RequestListener: NimbusRequestManagerDelegate, Sendable {

        nonisolated(unsafe) var continuation:
            UnsafeContinuation<NimbusAd, Error>?

        func didCompleteNimbusRequest(request: NimbusRequest, ad: NimbusAd) {
            continuation?.resume(returning: ad)
            continuation = nil
        }

        func didFailNimbusRequest(request: NimbusRequest, error: NimbusError) {
            continuation?.resume(throwing: error)
            continuation = nil
        }
    }

    public func fetch(request: NimbusRequest) async throws -> NimbusAd {
        let listener = RequestListener()
        delegate = listener
        let bid = try await withTaskCancellationHandler {
            try await withUnsafeThrowingContinuation { continuation in
                listener.continuation = continuation
                performRequest(request: request)
            }
        } onCancel: {
            listener.continuation?.resume(throwing: CancellationError())
            listener.continuation = nil
        }
        return bid
    }
}
