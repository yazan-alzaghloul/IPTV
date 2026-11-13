import IPTVCore
import Network
import SwiftUI
import UniformTypeIdentifiers

struct AppearanceDocument: FileDocument {
  static var readableContentTypes: [UTType] { [.json] }
  var data: Data
  init(data: Data = Data()) { self.data = data }
  init(configuration: ReadConfiguration) throws {
    data = configuration.file.regularFileContents ?? Data()
  }
  func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
    FileWrapper(regularFileWithContents: data)
  }
}
@MainActor final class NetworkStatus: ObservableObject {
  @Published private(set) var connected = false
  private let monitor = NWPathMonitor()
  init() {
    monitor.pathUpdateHandler = { [weak self] path in
      Task { @MainActor in self?.connected = path.status == .satisfied }
    }
    monitor.start(queue: DispatchQueue(label: "IPTV.Network"))
  }
  deinit { monitor.cancel() }
}
struct SettingsView: View {
  @EnvironmentObject private var model: AppModel
  @StateObject private var network = NetworkStatus()
  @State private var importing = false
  @State private var exporting = false
  @State private var clear = false
  @State private var signOut = false
  @State private var message: String?
  @State private var document = AppearanceDocument()
  var body: some View {
    Form {
      SwiftUI.Section(localized("Appearance")) {
        Picker(localized("Theme"), selection: $model.appearance.theme) {
          Text(localized("Cinematic dark")).tag("dark")
          Text(localized("Light")).tag("light")
        }
        Picker(localized("Accent"), selection: $model.appearance.accent) {
          Text(localized("Violet")).tag("violet")
          Text(localized("Ocean")).tag("cyan")
        }
        Toggle(localized("High contrast"), isOn: $model.appearance.highContrast)
        VStack(alignment: .leading) {
          Text(localized("Text size") + " \(Int(model.appearance.fontScale * 100))%")
          Slider(value: $model.appearance.fontScale, in: 0.85...1.4, step: 0.05).accessibilityLabel(
            localized("Text size"))
          Text(localized("Your device’s system text size is also respected.")).font(.footnote)
            .foregroundStyle(CinemaSecondary())
        }
      }
      SwiftUI.Section(localized("Home")) {
        Picker(localized("Open app to"), selection: $model.appearance.landing) {
          ForEach([Section.home, .live, .movies, .series]) {
            Text(localized($0.title)).tag($0.rawValue)
          }
        }
        Toggle(localized("Optional motion"), isOn: $model.appearance.motion)
        Toggle(localized("Rotate featured artwork"), isOn: $model.appearance.heroRotation)
        ForEach(
          [
            "Continue watching", "Recently watched channels", "Favorites", "Live TV", "Movies",
            "Series",
          ], id: \.self
        ) { row in
          Toggle(
            localized(row),
            isOn: Binding(
              get: { !model.appearance.hiddenRows.contains(row) },
              set: { visible in
                if visible {
                  model.appearance.hiddenRows.remove(row)
                } else {
                  model.appearance.hiddenRows.insert(row)
                }
              }))
        }
      }
      SwiftUI.Section(localized("Movies & episodes")) {
        Toggle(localized("Autoplay next episode"), isOn: $model.autoplay)
        Picker(localized("Mark complete at"), selection: $model.completion) {
          ForEach([90, 95, 98, 100], id: \.self) { Text("\($0)%").tag($0) }
        }
        Text(localized("Audio, subtitles and Picture in Picture use the system video controls."))
          .font(
            .footnote
          ).foregroundStyle(CinemaSecondary())
      }
      SwiftUI.Section(localized("Data & privacy")) {
        NavigationLink(localized("Watch history")) { HistoryView() }
        Button(localized("Clear watch history"), role: .destructive) { clear = true }
        Button(localized("Clear artwork cache")) {
          Task {
            await ArtworkCache.shared.clear()
            message = localized("Artwork cache cleared.")
          }
        }
        Button(localized("Export appearance")) {
          do {
            document = AppearanceDocument(data: try JSONEncoder().encode(model.appearance))
            exporting = true
          } catch { message = localized("Could not export settings. Try another location.") }
        }
        Button(localized("Import appearance")) { importing = true }
        Button(localized("Reset appearance")) {
          model.appearance = Appearance()
          message = localized("Appearance reset.")
        }
        Text(localized("Provider credentials are excluded from appearance exports.")).font(
          .footnote
        )
        .foregroundStyle(CinemaSecondary())
      }
      SwiftUI.Section(localized("About & diagnostics")) {
        LabeledContent(
          localized("Version"),
          value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.3")
        LabeledContent("iOS", value: UIDevice.current.systemVersion)
        LabeledContent(
          localized("Network"), value: localized(network.connected ? "Connected" : "Offline"))
        LabeledContent(
          localized("Device memory"),
          value: "\(ProcessInfo.processInfo.physicalMemory / 1_048_576) MB")
        Text(
          localized("Some provider formats and codecs require an HLS or MP4 alternative on iOS.")
        ).font(
          .footnote
        ).foregroundStyle(CinemaSecondary())
      }
      SwiftUI.Section {
        Button(localized("Sign out"), role: .destructive) { signOut = true }
          .accessibilityIdentifier(
            "settings.signout")
        if let message { Text(message).foregroundStyle(Cinema.accentText) }
        if let error = model.error { ErrorBanner(message: error) }
      }
    }.navigationTitle(localized("Settings"))
      .fileExporter(
        isPresented: $exporting, document: document, contentType: .json,
        defaultFilename: "Rayan-TV-appearance"
      ) { result in
        switch result {
        case .success: message = localized("Appearance exported.")
        case .failure: message = localized("Could not export settings. Try another location.")
        }
      }
      .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText]) { result in
        do {
          let url = try result.get()
          let granted = url.startAccessingSecurityScopedResource()
          defer { if granted { url.stopAccessingSecurityScopedResource() } }
          let values = try url.resourceValues(forKeys: [.fileSizeKey])
          guard (values.fileSize ?? Int.max) <= 32_768 else {
            throw IPTVError.message("This appearance file is too large.")
          }
          model.appearance = try Appearance.imported(Data(contentsOf: url))
          message = localized("Appearance imported.")
        } catch { message = localized("This is not a supported appearance settings file.") }
      }
      .confirmationDialog(
        localized("Clear watch history?"), isPresented: $clear, titleVisibility: .visible
      ) {
        Button(localized("Clear history"), role: .destructive) { model.clearHistory() }
      } message: {
        Text(
          localized(
            "This also resets saved progress for titles in your recent history. Favorites stay saved."
          ))
      }
      .confirmationDialog(
        localized("Sign out and remove saved account data?"), isPresented: $signOut,
        titleVisibility: .visible
      ) { Button(localized("Sign out"), role: .destructive) { Task { await model.signOut() } } }
  }
}
