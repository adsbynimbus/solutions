import Foundation
import NimbusKit

final class UpdatedIABVerificationProvider: NimbusKit.Configuration.VerificationProvider {

    let verificationUrl = URL(string: "https://\(Bundle.main.infoDictionary?["Compliance Script URL"] as! String)")!

    func verificationMarkup(response: NimbusResponse) -> String {
        guard let range = response.bid.adm.range(
            of: "</body>",
            options: .backwards
        ) else { return response.bid.adm }

        var modifiedMarkup = response.bid.adm
        modifiedMarkup.insert(
            contentsOf: getScriptContents(),
            at: range.lowerBound
        )
        return modifiedMarkup
    }

    func verificationResource(response: NimbusResponse) -> Configuration.VerificationScriptResource? {
        Configuration.VerificationScriptResource(
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
