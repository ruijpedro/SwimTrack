import SwiftUI

@main
struct SwimTrackApp: App {
    @StateObject private var store = SwimStore()
    var body: some Scene {
        WindowGroup { ContentView().environmentObject(store) }
    }
}
