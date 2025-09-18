//
//  DynamicPriceTests.swift
//  DynamicPriceTests
//
//  Created by Jason Sznol on 9/17/25.
//  Copyright © 2025 AdsByNimbus. All rights reserved.
//

import DynamicPrice
import Foundation
import Testing

struct BiddersTest {
    
    struct TestBidder : Bidder {
        let seconds: Double
        let bid: Bid?
        
        func fetchBid() async throws -> Bid {
            nonisolated(unsafe) var continuation: UnsafeContinuation<Bid, Error>?
            let result: Bid = try await withTaskCancellationHandler {
                try await withUnsafeThrowingContinuation { c in
                    continuation = c
                    DispatchQueue.global().asyncAfter(deadline: .now() + seconds) {
                        if let bid = self.bid {
                            continuation?.resume(returning: bid)
                        } else {
                            continuation?.resume(throwing: MockError())
                        }
                        continuation = nil
                    }
                }
            }
            onCancel: {
                continuation?.resume(throwing: CancellationError())
                continuation = nil
            }
            return result
        }
    }
    
    struct MockError : Error { }
    
    let clock = ContinuousClock()

    @Test("Test successful auction") func testAuction() async throws {
        let bidders: [Bidder] = [
            TestBidder(seconds: 1, bid: .test),
            TestBidder(seconds: 1, bid: .test),
        ]
        let startTime = clock.now
        let result = await bidders.auction()
        #expect(clock.now - startTime < .seconds(1.1))
        #expect(result.count == 2)
    }
    
    @Test("Test auction with failed bidder") func testAuctionFailure() async throws {
        let bidders: [Bidder] = [
            TestBidder(seconds: 1, bid: .test),
            TestBidder(seconds: 1, bid: nil),
        ]
        let startTime = clock.now
        let result = await bidders.auction()
        #expect(clock.now - startTime < .seconds(1.1))
        #expect(result.count == 1)
    }
    
    @Test("Test auction with timeout") func testAuctionTimeout() async throws {
        let bidders: [Bidder] = [
            TestBidder(seconds: 1, bid: .test),
            // The following 2 bidders should timeout
            TestBidder(seconds: 4, bid: .test),
            TestBidder(seconds: 5, bid: nil)
        ]
        let startTime = clock.now
        let result = await bidders.auction()
        #expect(clock.now - startTime < .seconds(3.1))
        #expect(result.count == 1)
    }
}
