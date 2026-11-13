import SwiftUI

struct LoginView: View {
  @EnvironmentObject private var model: AppModel
  @State private var host = ""
  @State private var port = ""
  @State private var scheme = "https"
  @State private var username = ""
  @State private var password = ""
  @State private var visible = false
  var body: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 18) {
        Text("IPTV").font(.largeTitle.bold()).foregroundStyle(Cinema.accentText)
        Text(localized("Your channels. One place.")).font(.title.bold())
        Text(localized("Sign in with your IPTV provider’s Xtream account.")).foregroundStyle(
          CinemaSecondary())
        Picker(localized("Protocol"), selection: $scheme) {
          Text("HTTPS").tag("https")
          Text("HTTP").tag("http")
        }.pickerStyle(.segmented)
        if scheme == "http" || host.lowercased().hasPrefix("http://") {
          Text(
            localized(
              "HTTP sends your login and video without encryption. Use HTTPS when your provider supports it."
            )
          ).font(.footnote).foregroundStyle(CinemaSecondary())
        }
        TextField(localized("Host or provider URL"), text: $host).keyboardType(.URL)
          .textContentType(.URL)
          .accessibilityIdentifier("login.host")
        TextField(localized("Port (optional)"), text: $port).keyboardType(.numberPad)
          .accessibilityIdentifier("login.port")
        TextField(localized("Username"), text: $username).textContentType(.username)
          .accessibilityIdentifier("login.username")
        HStack {
          Group {
            if visible {
              TextField(localized("Password"), text: $password)
            } else {
              SecureField(localized("Password"), text: $password)
            }
          }.textContentType(.password).accessibilityIdentifier("login.password")
          Button(visible ? localized("Hide") : localized("Show")) { visible.toggle() }.frame(
            minWidth: 44, minHeight: 44)
        }
        if let error = model.error { ErrorBanner(message: error) }
        Button {
          Task {
            await model.login(
              host: host, port: port, scheme: scheme, username: username, password: password)
            if model.credentials != nil { password = "" }
          }
        } label: {
          HStack {
            if model.busy { ProgressView().tint(.white) }
            Text(model.busy ? localized("Connecting") : localized("Connect")).frame(
              maxWidth: .infinity)
          }.frame(minHeight: 44)
        }.buttonStyle(CinemaPrimaryButtonStyle()).accessibilityIdentifier("login.connect")
        Text(
          localized(
            "Your login stays on this device. Channels and titles come from your subscription.")
        )
        .font(.footnote).foregroundStyle(CinemaSecondary())
      }.textFieldStyle(.roundedBorder).textInputAutocapitalization(.never).autocorrectionDisabled()
        .disabled(model.busy)
        .padding(24).frame(maxWidth: 520).frame(maxWidth: .infinity).modifier(ScaledText())
    }.background(Cinema.background).scrollDismissesKeyboard(.interactively)
  }
}
