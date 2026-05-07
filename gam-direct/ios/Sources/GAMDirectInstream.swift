//
//  GAMDirectInstream.swift
//  GAMDirect
//
//  Created by Jason Sznol on 5/7/26.
//  Copyright © 2026 AdsByNimbus. All rights reserved.
//

import AVFoundation
import GoogleInteractiveMediaAds
import NimbusKit
import SwiftUI

struct GAMDirectInstream: UIViewControllerRepresentable {
    typealias UIViewControllerType = PlayerContainerViewController

    func makeUIViewController(context: Context) -> PlayerContainerViewController {
        return PlayerContainerViewController()
    }

    func updateUIViewController(_ uiViewController: PlayerContainerViewController, context: Context) {}
}

class PlayerContainerViewController: UIViewController,
    @MainActor IMAAdsLoaderDelegate, @MainActor IMAAdsManagerDelegate {
    static let contentURL = URL(
        string: "https://storage.googleapis.com/gvabox/media/samples/stock.mp4"
    )!
    // The Google tag requires nofb=1 and &cust_params=nimbus%3Ddirect appended to it
    static let adTagURLString = Bundle.main.infoDictionary?["GAM Direct Instream Tag"] as! String +
        "&nofb=1" + "&cust_params=nimbus%3Ddirect"

    private var contentPlayer = AVPlayer(url: PlayerContainerViewController.contentURL)

    private lazy var playerLayer: AVPlayerLayer = {
        AVPlayerLayer(player: contentPlayer)
    }()

    private let adsLoader = IMAAdsLoader()
    private var adsManager: IMAAdsManager?

    private lazy var contentPlayhead: IMAAVPlayerContentPlayhead = {
        IMAAVPlayerContentPlayhead(avPlayer: contentPlayer)
    }()

    private lazy var videoView: UIView = {
        let videoView = UIView()
        videoView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(videoView)

        NSLayoutConstraint.activate([
            videoView.bottomAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.bottomAnchor
            ),
            videoView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            videoView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            videoView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
        ])
        return videoView
    }()

    override func viewDidLoad() {
        super.viewDidLoad()

        videoView.layer.addSublayer(playerLayer)
        adsLoader.delegate = self

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(contentDidFinishPlaying(_:)),
            name: .AVPlayerItemDidPlayToEndTime,
            object: contentPlayer.currentItem
        )

        requestAds()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        playerLayer.frame = videoView.layer.bounds
    }

    override func viewWillTransition(
        to size: CGSize,
        with coordinator: UIViewControllerTransitionCoordinator
    ) {
        coordinator.animate { _ in
        } completion: { _ in
            self.playerLayer.frame = self.videoView.layer.bounds
        }
    }



    private func requestAds() {
        // Create ad display container for ad rendering.
        let adDisplayContainer = IMAAdDisplayContainer(
            adContainer: videoView,
            viewController: self,
            companionSlots: nil
        )

        // The user context of "direct" will be used to identify the request in the error handler
        let request = IMAAdsRequest(
            adTagUrl: PlayerContainerViewController.adTagURLString,
            adDisplayContainer: adDisplayContainer,
            contentPlayhead: contentPlayhead,
            userContext: "direct"
        )

        print("Requesting Direct Deal from Google")
        adsLoader.requestAds(with: request)
    }

    func adsLoader(_ loader: IMAAdsLoader, failedWith adErrorData: IMAAdLoadingErrorData) {
        guard "direct" == adErrorData.userContext as? String else {
            print("Error loading Nimbus Ad, starting playback")
            contentPlayer.play()
            return
        }

        Task {
            let nimbusRequest = NimbusRequest.forVideoAd(position: "instream")
            nimbusRequest.impressions[0].video?.placementType = .inStream
            nimbusRequest.impressions[0].video?.width = 300 // Use the video player width
            nimbusRequest.impressions[0].video?.height = 250 // Use the video player height

            // Fetch ad can be found in Nimbus+Async.swift
            guard let nimbusAd = try? await nimbusRequest.fetchAd() else {
                print("Nimbus did not return a bid, starting playback")
                contentPlayer.play()
                return
            }

            let adDisplayContainer = IMAAdDisplayContainer(
                adContainer: videoView,
                viewController: self,
                companionSlots: nil
            )

            let nimbusImaAd = IMAAdsRequest(
                adsResponse: nimbusAd.markup,
                adDisplayContainer: adDisplayContainer,
                contentPlayhead: contentPlayhead,
                userContext: nil
            )

            print("Loading Nimbus Ad using IMA")
            loader.requestAds(with: nimbusImaAd)
        }
    }

    func adsLoader(_ loader: IMAAdsLoader, adsLoadedWith adsLoadedData: IMAAdsLoadedData) {
        // Grab the instance of the IMAAdsManager and set ourselves as the delegate.
        adsManager = adsLoadedData.adsManager
        adsManager?.delegate = self

        let adsRenderingSettings = IMAAdsRenderingSettings()
        adsRenderingSettings.linkOpenerPresentingController = self

        adsManager?.initialize(with: adsRenderingSettings)
    }

    func adsManager(_ adsManager: IMAAdsManager, didReceive event: IMAAdEvent) {
        if event.type == IMAAdEventType.LOADED {
            adsManager.start()
        }
    }

    func adsManager(_ adsManager: IMAAdsManager, didReceive error: IMAAdError) {
        if let message = error.message {
            print("AdsManager error: \(message)")
        }
        contentPlayer.play()
    }

    func adsManagerDidRequestContentPause(_ adsManager: IMAAdsManager) {
        contentPlayer.pause()
    }

    func adsManagerDidRequestContentResume(_ adsManager: IMAAdsManager) {
        contentPlayer.play()
    }

    @objc func contentDidFinishPlaying(_ notification: Notification) {
        // Make sure we don't call contentComplete as a result of an ad completing.
        if notification.object as? AVPlayerItem == contentPlayer.currentItem {
            adsLoader.contentComplete()
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}
