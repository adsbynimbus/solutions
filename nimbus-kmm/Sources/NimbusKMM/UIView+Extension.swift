//
//  UIView+Extension.swift
//  NimbusKMM
//
//  Created by Jason Sznol on 4/8/25.
//

import UIKit

public extension UIView {
    @objc var parentViewController: UIViewController {
        var responder: UIResponder? = self
        while !(responder is UIViewController) {
            responder = responder?.next
            if nil == responder {
                break
            }
        }
        return (responder as? UIViewController)!
    }
}
