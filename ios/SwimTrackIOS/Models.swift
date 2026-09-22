import Foundation

struct SwimTime: Identifiable, Codable, Hashable {
    var id: String { "\(pool)#\(event)" }
    var event: String
    var pool: String
    var time: String
    var date: String
    var city: String
    var source: String
}

struct AthleteProfile: Codable {
    var name = ""
    var year = ""
    var club = ""
    var country = "Portugal"
    var sex = "F"
    var seasonStart = "2026"
    var categoryManual = ""
    var athleteId = "5631298"
    var clubDivision: Int? = 3
}


struct SwimMeet: Identifiable, Hashable, Codable {
    var id = UUID()
    var name: String
    var place: String
    var date: String
    var categories: String
    var organizer: String
    var scope: String
    var clubDivision: Int?
    var season: String = ""
}
