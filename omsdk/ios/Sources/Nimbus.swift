import NimbusKit
import OMSDK_Adsbynimbus

final class UpdatedIABVerificationProvider: NimbusVerificationProvider {

    let verificationUrl = URL(string: "https://\(Bundle.main.infoDictionary?["Compliance Script URL"] as! String)")!
    
    func verificationMarkup(ad: NimbusAd) -> String {
        guard let range = ad.markup.range(
            of: "</body>",
            options: .backwards
        ) else { return ad.markup }
        
        var modifiedMarkup = ad.markup
        modifiedMarkup.insert(
            contentsOf: getScriptContents(),
            at: range.lowerBound
        )
        return modifiedMarkup
    }

    func verificationResource(ad: NimbusAd) -> OMIDAdsbynimbusVerificationScriptResource? {
        OMIDAdsbynimbusVerificationScriptResource(
            url: verificationUrl,
            vendorKey: "iabtechlab.com-omid",
            parameters: "iabtechlab-Adsbynimbus"
        )
    }
    
    private func getScriptContents() -> String {
        """
        <script src="\(verificationUrl.absoluteString)" type="text/javascript"></script>
        """
    }
}
