import UIKit
import XCTest

final class IPTVUITests: XCTestCase {
  override func setUp() {
    continueAfterFailure = false
    executionTimeAllowance = 180
  }
  @MainActor private func application() -> XCUIApplication {
    if UIDevice.current.userInterfaceIdiom == .pad {
      XCUIDevice.shared.orientation = .landscapeLeft
    }
    let app = XCUIApplication()
    app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
    app.launch()
    return app
  }
  @MainActor private func capture(_ app: XCUIApplication, _ name: String) {
    let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    attachment.name = name.replacingOccurrences(
      of: "phone", with: UIDevice.current.userInterfaceIdiom == .pad ? "ipad" : "phone")
    attachment.lifetime = .keepAlways
    add(attachment)
  }
  @MainActor private func reveal(_ app: XCUIApplication, _ element: XCUIElement) {
    for _ in 0..<12 {
      if element.isHittable { return }
      app.swipeUp()
    }
  }
  @MainActor private func navigate(_ app: XCUIApplication, _ name: String) {
    if app.tabBars.buttons[name].exists {
      app.tabBars.buttons[name].tap()
      return
    }
    let label = app.staticTexts[name].firstMatch
    if !label.isHittable, app.navigationBars.buttons["Show Sidebar"].firstMatch.exists {
      app.navigationBars.buttons["Show Sidebar"].firstMatch.tap()
    }
    label.tap()
  }
  @MainActor private func secondary(_ app: XCUIApplication, _ name: String) {
    if app.tabBars.buttons["More"].exists {
      app.tabBars.buttons["More"].tap()
      app.buttons[name].tap()
    } else {
      navigate(app, name)
    }
  }
  @MainActor private func signOutIfNeeded(_ app: XCUIApplication) {
    if app.buttons["login.connect"].waitForExistence(timeout: 3) { return }
    secondary(app, "Settings")
    reveal(app, app.buttons["settings.signout"])
    app.buttons["settings.signout"].tap()
    let confirmation = app.buttons.matching(identifier: "Sign out").matching(
      NSPredicate(format: "identifier != %@", "settings.signout")
    ).firstMatch
    XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
    confirmation.tap()
    XCTAssertTrue(app.buttons["login.connect"].waitForExistence(timeout: 15))
  }
  @MainActor func testArabicLargeTextLogin() throws {
    let app = XCUIApplication()
    app.launchArguments = [
      "-AppleLanguages", "(ar)", "-AppleLocale", "ar_AE", "-UIPreferredContentSizeCategoryName",
      "UICTContentSizeCategoryAccessibilityXXXL",
    ]
    app.launch()
    guard app.buttons["login.connect"].waitForExistence(timeout: 10) else {
      throw XCTSkip("Arabic login snapshot needs a signed-out simulator.")
    }
    XCTAssertTrue(app.staticTexts["قنواتك في مكان واحد."].exists)
    capture(app, "arabic-large-text-phone-top")
    reveal(app, app.buttons["login.connect"])
    app.buttons["login.connect"].tap()
    XCTAssertTrue(app.staticTexts["error.message"].waitForExistence(timeout: 5))
    capture(app, "arabic-large-text-phone-form")
  }
  @MainActor func testLoginValidation() throws {
    let app = application()
    signOutIfNeeded(app)
    capture(app, "login-phone")
    app.buttons["login.connect"].tap()
    XCTAssertTrue(app.staticTexts["error.message"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.textFields["login.host"].exists)
  }
  @MainActor func testFixtureProviderFlow() throws {
    guard let host = ProcessInfo.processInfo.environment["IPTV_FIXTURE_URL"] else {
      throw XCTSkip("Use scripts/test-ios-fixture.sh for the HTTP integration flow.")
    }
    let app = application()
    signOutIfNeeded(app)
    for (id, text) in [("login.host", host), ("login.username", "fixture")] {
      app.textFields[id].tap()
      app.textFields[id].typeText(text)
    }
    app.secureTextFields["login.password"].tap()
    app.secureTextFields["login.password"].typeText("fixture\n")
    app.buttons["login.connect"].tap()
    XCTAssertTrue(app.staticTexts["Fixture Movie"].firstMatch.waitForExistence(timeout: 30))
    capture(app, "home-phone")
    navigate(app, "Movies")
    XCTAssertTrue(app.buttons["Details"].firstMatch.waitForExistence(timeout: 15))
    app.buttons["Details"].firstMatch.tap()
    XCTAssertTrue(app.staticTexts["Fixture movie plot"].firstMatch.waitForExistence(timeout: 15))
    capture(app, "movie-details-phone")
    app.buttons["Watchlist"].firstMatch.tap()
    app.buttons["Play now"].firstMatch.tap()
    XCTAssertTrue(app.buttons["Info"].waitForExistence(timeout: 15))
    let buffering = app.progressIndicators["Buffering"]
    let ready = XCTNSPredicateExpectation(
      predicate: NSPredicate(format: "exists == false"), object: buffering)
    XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 20), .completed)
    capture(app, "player-phone")
    app.buttons["Info"].tap()
    let diagnostics = app.alerts.staticTexts.matching(
      NSPredicate(format: "label BEGINSWITH %@", "AVPlayer")
    ).firstMatch
    XCTAssertTrue(diagnostics.waitForExistence(timeout: 5))
    XCTAssertFalse(
      diagnostics.label.contains("0 × 0"), "Real media should have a video presentation size.")
    app.alerts.buttons["Close"].tap()
    app.buttons["Close"].firstMatch.tap()
    navigate(app, "Live TV")
    if app.buttons["Guide and preview: Fixture News"].firstMatch.exists {
      app.buttons["Guide and preview: Fixture News"].firstMatch.tap()
    }
    XCTAssertTrue(app.staticTexts["News now"].firstMatch.waitForExistence(timeout: 15))
    capture(app, "live-guide-phone")
    app.buttons["Start preview"].tap()
    XCTAssertTrue(app.otherElements["live.preview"].waitForExistence(timeout: 10))
    let previewReady = app.staticTexts["live.preview.ready"].waitForExistence(timeout: 20)
    capture(app, "live-preview-phone")
    XCTAssertTrue(previewReady)

    app.buttons["Stop preview"].tap()
    navigate(app, "Series")
    app.buttons["Details"].firstMatch.tap()
    reveal(app, app.buttons["series.markSeason"])
    XCTAssertEqual(app.buttons["series.markSeason"].value as? String, "2")
    app.buttons["series.markSeason"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
      .tap()
    XCTAssertTrue(app.buttons["Mark season unwatched"].waitForExistence(timeout: 5))
    capture(app, "series-phone")
    secondary(app, "Favorites")
    XCTAssertTrue(app.buttons["card.MOVIE:2"].firstMatch.waitForExistence(timeout: 10))
    app.buttons["card.MOVIE:2"].firstMatch.tap()
    XCTAssertTrue(app.staticTexts["Fixture movie plot"].firstMatch.waitForExistence(timeout: 10))
    app.navigationBars.buttons.element(boundBy: 0).tap()
    if app.tabBars.buttons["More"].exists { app.navigationBars.buttons.element(boundBy: 0).tap() }
    secondary(app, "Settings")
    capture(app, "settings-phone")
    reveal(app, app.buttons["settings.signout"])
    app.buttons["settings.signout"].tap()
    let confirmation = app.buttons.matching(identifier: "Sign out").matching(
      NSPredicate(format: "identifier != %@", "settings.signout")
    ).firstMatch
    XCTAssertTrue(confirmation.waitForExistence(timeout: 5))
    confirmation.tap()
    XCTAssertTrue(app.buttons["login.connect"].waitForExistence(timeout: 15))
  }
}
