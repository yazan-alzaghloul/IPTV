import IPTVCore
import SwiftUI

extension Color {
  init(hex: UInt32) {
    self.init(
      .sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
      blue: Double(hex & 255) / 255, opacity: 1)
  }
}
enum Cinema {
  static let background = Color(
    uiColor: UIColor {
      $0.userInterfaceStyle == .dark
        ? UIColor(red: 8 / 255, green: 11 / 255, blue: 18 / 255, alpha: 1)
        : UIColor(red: 244 / 255, green: 245 / 255, blue: 250 / 255, alpha: 1)
    })
  static let panel = Color(
    uiColor: UIColor {
      $0.userInterfaceStyle == .dark
        ? UIColor(red: 21 / 255, green: 27 / 255, blue: 40 / 255, alpha: 1) : .white
    })
  static let cyan = Color(hex: 0x38BDF8)
  static let accentText = Color(
    uiColor: UIColor {
      $0.userInterfaceStyle == .dark
        ? UIColor(red: 196 / 255, green: 181 / 255, blue: 253 / 255, alpha: 1)
        : UIColor(red: 91 / 255, green: 33 / 255, blue: 182 / 255, alpha: 1)
    })
}
struct CinemaHeading: View {
  var text: String
  var body: some View {
    Text(localized(text)).font(.title2.bold()).accessibilityAddTraits(.isHeader).frame(
      maxWidth: .infinity, alignment: .leading)
  }
}
struct ErrorBanner: View {
  let message: String
  var retry: (() -> Void)?
  var body: some View {
    VStack(alignment: .leading, spacing: 10) {
      Label(message, systemImage: "exclamationmark.triangle").foregroundStyle(.red)
        .accessibilityIdentifier("error.message")
      if let retry { Button(localized("Retry"), action: retry).buttonStyle(.bordered) }
    }.padding().frame(maxWidth: .infinity, alignment: .leading).background(
      Cinema.panel, in: RoundedRectangle(cornerRadius: 12))
  }
}
struct Artwork: View {
  let item: CatalogItem
  var backdrop = false
  var body: some View {
    CachedArtwork(
      url: URL(
        string: backdrop ? (item.backdrop.isEmpty ? item.image : item.backdrop) : item.image),
      fit: item.kind == .live && !backdrop
    ) {
      ZStack {
        Cinema.panel
        Image(systemName: item.kind == .live ? "tv" : "film").font(.largeTitle).foregroundStyle(
          Cinema.accentText)
      }
    }.accessibilityHidden(true).allowsHitTesting(false).clipShape(
      RoundedRectangle(cornerRadius: 12))
  }
}
struct MediaCard: View {
  @EnvironmentObject private var model: AppModel
  let item: CatalogItem
  var body: some View {
    NavigationLink {
      DetailsView(item: model.parent(item))
    } label: {
      VStack(alignment: .leading, spacing: 8) {
        Artwork(item: item).aspectRatio(item.kind == .live ? 16 / 9 : 2 / 3, contentMode: .fit)
          .overlay(alignment: .topTrailing) {
            if model.isFavorite(item) {
              Image(systemName: "heart.fill").padding(8).background(
                .black.opacity(0.7), in: Circle()
              ).foregroundStyle(.white).padding(6)
            }
          }
        Text(item.name).font(.headline).lineLimit(2).foregroundStyle(.primary)
        if !item.year.isEmpty { Text(item.year).font(.caption).foregroundStyle(CinemaSecondary()) }
        if let record = model.library.history.first(where: {
          $0.item.key == item.key || $0.item.parentSeriesID == item.id && item.kind == .series
        }), record.duration > 0 {
          ProgressView(value: min(1, record.position / record.duration)).accessibilityLabel(
            localized("Viewing progress"))
        }
      }.contentShape(Rectangle())
    }.buttonStyle(.plain).contentShape(Rectangle()).accessibilityIdentifier("card.\(item.key)")
      .contextMenu {
        Button(
          model.isFavorite(item)
            ? localized("Remove from favorites") : localized("Add to favorites"),
          systemImage: "heart"
        ) { model.toggle(item) }
        if item.kind != .live {
          Button(localized("Mark watched"), systemImage: "checkmark") {
            model.mark([item], watched: true)
          }
        }
      }
  }
}
struct MediaRail: View {
  let title: String
  let items: [CatalogItem]
  var body: some View {
    if !items.isEmpty {
      VStack(alignment: .leading, spacing: 12) {
        CinemaHeading(text: title).padding(.horizontal, 24)
        ScrollView(.horizontal) {
          LazyHStack(alignment: .top, spacing: 14) {
            ForEach(items, id: \.key) {
              MediaCard(item: $0).frame(width: $0.kind == .live ? 220 : 150)
            }
          }.padding(.horizontal, 24)
        }.scrollIndicators(.hidden)
      }
    }
  }
}
struct ScaledText: ViewModifier {
  @EnvironmentObject var model: AppModel
  @ScaledMetric private var size: CGFloat = 17
  func body(content: Content) -> some View { content.font(.system(size: size)) }
}

private struct CinemaHighContrastKey: EnvironmentKey { static let defaultValue = false }
extension EnvironmentValues {
  var cinemaHighContrast: Bool {
    get { self[CinemaHighContrastKey.self] }
    set { self[CinemaHighContrastKey.self] = newValue }
  }
}
struct CinemaSecondary: ShapeStyle {
  func resolve(in environment: EnvironmentValues) -> some ShapeStyle {
    environment.cinemaHighContrast || environment.colorSchemeContrast == .increased
      ? Color.primary : Color.secondary
  }
}

struct CinemaPrimaryButtonStyle: ButtonStyle {
  @EnvironmentObject private var model: AppModel
  @Environment(\.isEnabled) private var enabled
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .foregroundStyle(.white)
      .padding(.horizontal, 16).padding(.vertical, 10)
      .frame(minHeight: 44)
      .background(
        Color(
          hex: model.appearance.accent == "cyan"
            ? (model.appearance.theme == "light" ? 0x006875 : 0x006E96)
            : (model.appearance.theme == "light" ? 0x6D28D9 : 0x7C3AED)),
        in: RoundedRectangle(cornerRadius: 12)
      )
      .opacity(enabled ? (configuration.isPressed ? 0.75 : 1) : 0.45)
  }
}
