import Foundation
import PDFKit

@MainActor final class SwimStore: ObservableObject {
    @Published var profile = AthleteProfile()
    @Published var times: [SwimTime] = []
    @Published var previous: [SwimTime] = []
    @Published var tacs: [String:String] = [:]
    @Published var message = ""
    @Published var meets: [SwimMeet] = []
    @Published var calendarArchive: [String:[SwimMeet]] = [:]

    private let defaults = UserDefaults.standard
    init() { load(); if tacs.isEmpty { tacs = seedTac() }; if calendarArchive.isEmpty { let seed = seedCalendar2627(); calendarArchive["2026/27"] = seed }; syncActiveCalendar(); save() }

    var activeSeasonKey: String { let y = Int(profile.seasonStart) ?? 2026; return "\(y)/\(String((y+1)%100).leftPad2)" }
    var availableSeasons: [String] { Array(Set(calendarArchive.keys).union([activeSeasonKey])).sorted().reversed() }
    func selectSeason(_ key:String) { if let y=Int(key.prefix(4)) { profile.seasonStart=String(y); syncActiveCalendar(); save() } }
    func syncActiveCalendar() { meets = calendarArchive[activeSeasonKey] ?? [] }
    func setClubDivision(_ division:Int?) { profile.clubDivision = division; save() }

    var category: String {
        if !profile.categoryManual.isEmpty { return profile.categoryManual }
        guard let y = Int(profile.year), let season = Int(profile.seasonStart) else { return "Por definir" }
        let age = (season + 1) - y
        if profile.sex.uppercased().hasPrefix("F") {
            return [13:"Infantil B Feminino",14:"Infantil A Feminino",15:"Juvenil B Feminino",16:"Juvenil A Feminino",17:"Júnior Feminino 1.º ano",18:"Júnior Feminino 2.º ano"][age] ?? (age >= 19 ? "Sénior Feminino" : "Por definir")
        }
        return [14:"Infantil B Masculino",15:"Infantil A Masculino",16:"Juvenil B Masculino",17:"Juvenil A Masculino",18:"Júnior Masculino 1.º ano",19:"Júnior Masculino 2.º ano"][age] ?? (age >= 20 ? "Sénior Masculino" : "Por definir")
    }

    func importPDF(_ url: URL) {
        guard url.startAccessingSecurityScopedResource() else { message = "Sem acesso ao PDF."; return }
        defer { url.stopAccessingSecurityScopedResource() }
        guard let doc = PDFDocument(url: url) else { message = "Não foi possível abrir o PDF."; return }
        var text = ""
        for i in 0..<doc.pageCount { text += (doc.page(at: i)?.string ?? "") + "\n" }
        let count = importSwimrankingsText(text)
        message = count > 0 ? "Importados \(count) recordes do Swimrankings." : "Não encontrei recordes no formato Swimrankings."
    }

    @discardableResult func importSwimrankingsText(_ raw: String) -> Int {
        guard !raw.isEmpty else { return 0 }
        importProfile(raw)
        let lines = raw.replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n").map { $0.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        var pool = "", stroke = ""
        var found: [SwimTime] = []
        let styles = "(Livres|Costas|Bruços|Brucos|Mariposa|Estilos)"
        let pattern = "^(?:\(styles)\\s+)?(50|100|200|400|800|1500)m\\s+(\\d{1,2}:\\d{2}[.,]\\d{2}|\\d{2}[.,]\\d{2})\\s+(\\d{1,2}\\s+[A-Za-zÀ-ÿ]{3}\\s+\\d{4})(?:\\s+(.+))?$"
        let re = try! NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
        let styleRe = try! NSRegularExpression(pattern: "^\(styles)(?:\\s+(?:50|100|200|400|800|1500)m)?$", options: [.caseInsensitive])
        for line in lines {
            if line.localizedCaseInsensitiveContains("Piscina longa") { pool="50m"; stroke=""; continue }
            if line.localizedCaseInsensitiveContains("Piscina curta") { pool="25m"; stroke=""; continue }
            let ns = line as NSString, range = NSRange(location:0,length:ns.length)
            if let m = re.firstMatch(in: line, range: range), !pool.isEmpty {
                let s = m.range(at:1).location != NSNotFound ? ns.substring(with:m.range(at:1)) : ""
                if !s.isEmpty { stroke = normalizeStroke(s) }
                guard !stroke.isEmpty else { continue }
                let distance=ns.substring(with:m.range(at:2)), time=normalizeTime(ns.substring(with:m.range(at:3))), date=ns.substring(with:m.range(at:4))
                let city = m.range(at:5).location != NSNotFound ? ns.substring(with:m.range(at:5)).trimmingCharacters(in:.whitespaces) : "-"
                if !time.isEmpty { found.append(.init(event:"\(distance) \(stroke)",pool:pool,time:time,date:date,city:city.isEmpty ? "-":city,source:"Swimrankings PDF")) }
                continue
            }
            if let m=styleRe.firstMatch(in:line,range:range), m.range(at:1).location != NSNotFound { stroke=normalizeStroke(ns.substring(with:m.range(at:1))) }
        }
        if !found.isEmpty { if !times.isEmpty { previous=times }; times=selectBest(found); save() }
        return Set(found.map{$0.id}).count
    }

    func importProfile(_ text:String) {
        guard let line=text.components(separatedBy:.newlines).map({$0.replacingOccurrences(of:"\\s+",with:" ",options:.regularExpression).trimmingCharacters(in:.whitespaces)}).first(where:{$0.localizedCaseInsensitiveContains("POR - Portugal")}) else { return }
        let re=try! NSRegularExpression(pattern:"\\b(19|20)\\d{2}\\b")
        let ns=line as NSString, r=NSRange(location:0,length:ns.length)
        if let m=re.firstMatch(in:line,range:r) {
            let year=ns.substring(with:m.range); profile.year=year
            profile.name=String(line.prefix(m.range.location)).replacingOccurrences(of:"Software",with:"").trimmingCharacters(in:.whitespaces)
        }
        if let rr=line.range(of:"POR - Portugal",options:.caseInsensitive) { profile.club=String(line[rr.upperBound...]).trimmingCharacters(in:.whitespaces) }
        profile.country="Portugal"
        if profile.name.folding(options:.diacriticInsensitive,locale:.current).lowercased().contains("constanca") { profile.sex="F" }
        save()
    }

    func upsertTime(_ t:SwimTime) { times.removeAll{$0.id==t.id}; times.append(t); times=selectBest(times); save() }
    func deleteTime(_ t:SwimTime) { times.removeAll{$0.id==t.id}; save() }
    func tac(for t:SwimTime)->String { tacs[key(t.event,t.pool)] ?? "" }
    func setTac(event:String,pool:String,time:String) { tacs[key(normalizeEvent(event),normalizePool(pool))]=normalizeTime(time); save() }
    func seconds(_ v:String)->Double { let x=v.replacingOccurrences(of:",",with:"."); if x.contains(":") { let p=x.split(separator:":"); return (Double(p[0]) ?? 99999)*60+(Double(p[1]) ?? 0) }; return Double(x) ?? 99999 }
    func status(_ t:SwimTime)->String { let tac=tac(for:t); guard !tac.isEmpty else{return "TAC por definir"}; let d=seconds(t.time)-seconds(tac); return d<=0 ? String(format:"QUALIFICADA • margem %.2f s",-d) : String(format:"Faltam %.2f s",d) }
    func isQualified(_ t:SwimTime)->Bool { let x=tac(for:t); return !x.isEmpty && seconds(t.time)<=seconds(x) }

    private func seedCalendar2627() -> [SwimMeet] {
        [
            .init(name:"Prova de Abertura de Categorias",place:"Benedita",date:"17–18 out. 2026",categories:"Infantis, Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Torneio dos 50 e 100",place:"Pombal",date:"31 out. 2026",categories:"Infantis, Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonato Distrital de PC + Fundo",place:"Leiria",date:"21–22 nov. 2026",categories:"Infantis, Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonato Nacional de Clubes 3.ª Divisão",place:"Mealhada",date:"27 nov. 2026",categories:"Absolutos",organizer:"ANCNP",scope:"Nacional (FPN)",clubDivision:3),
            .init(name:"Campeonato Nacional de Juniores e Seniores de PC",place:"Tomar",date:"10–13 dez. 2026",categories:"Juniores e Seniores",organizer:"ANDS",scope:"Nacional (FPN)",clubDivision:nil),
            .init(name:"Torneio de Natal + 1.º Torregri Cadetes",place:"Leiria",date:"19 dez. 2026",categories:"Cadetes, Infantis, Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonato Distrital de Inverno de Categorias",place:"Leiria",date:"23–24 jan. 2027",categories:"Infantis, Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Torneio Taça Cidade de Alcobaça",place:"Alcobaça",date:"30 jan. 2027",categories:"Infantis, Juvenis, Juniores e Seniores",organizer:"CNAL",scope:"ANDL",clubDivision:nil),
            .init(name:"Torneio Cidade de Pombal Infantis e Absolutos",place:"Pombal",date:"21 fev. 2027",categories:"Infantis, Juvenis, Juniores e Seniores",organizer:"NDAP",scope:"ANDL",clubDivision:nil),
            .init(name:"Taça ANDL João da Silva Abreu",place:"Caldas da Rainha",date:"6–7 mar. 2027",categories:"Absolutos",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonato Interdistrital de Juvenis, Juniores e Seniores",place:"N/D",date:"19–21 mar. 2027",categories:"Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonatos Nacionais de Juniores, Sub21 e Absolutos — Open Portugal / Astralpool",place:"Coimbra",date:"8–11 abr. 2027",categories:"Juniores, Sub21 e Absolutos",organizer:"ANC",scope:"Nacional (FPN)",clubDivision:nil),
            .init(name:"Prova de Preparação de Categorias",place:"Benedita",date:"10 abr. 2027",categories:"Cadetes, Infantis, Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"LEIRIASWIM",place:"Leiria",date:"maio 2027",categories:"Categorias",organizer:"ADBA",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonato Distrital de Verão",place:"Leiria",date:"26–27 jun. 2027",categories:"Infantis, Juvenis, Juniores e Seniores",organizer:"ANDL",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonato Interdistrital de Verão",place:"Coimbra",date:"15–18 jul. 2027",categories:"Juvenis, Juniores e Seniores",organizer:"ANC",scope:"ANDL",clubDivision:nil),
            .init(name:"Campeonatos Nacionais de Juvenis, Juniores e Seniores",place:"Coimbra",date:"29 jul.–1 ago. 2027",categories:"Juvenis, Juniores e Seniores",organizer:"ANC",scope:"Nacional (FPN)",clubDivision:nil)
        ]
    }

    func relevantMeet(_ meet: SwimMeet) -> Bool {
        let c = meet.categories.folding(options:.diacriticInsensitive, locale:.current).lowercased()
        let n = meet.name.folding(options:.diacriticInsensitive, locale:.current).lowercased()
        let junior = c.contains("junior") || c.contains("categorias")
        let absolute = c.contains("absolut")
        let thirdDivision = profile.clubDivision == 3 && ((meet.clubDivision == 3) || n.contains("3. divisao") || n.contains("3ª divisao"))
        return junior || absolute || thirdDivision
    }

    func importCalendar(_ url: URL) {
        guard url.startAccessingSecurityScopedResource() else { message = "Sem acesso ao calendário."; return }
        defer { url.stopAccessingSecurityScopedResource() }
        var text = ""
        if url.pathExtension.lowercased() == "pdf", let doc = PDFDocument(url: url) {
            for i in 0..<doc.pageCount { text += (doc.page(at: i)?.string ?? "") + "\n" }
        } else { text = (try? String(contentsOf: url, encoding: .utf8)) ?? "" }
        let count = importCalendarText(text)
        message = count > 0 ? "Calendário importado: \(count) competições." : "Não foi possível reconhecer competições. Usa PDF anual ou CSV: Nome;Local;Data;Categorias;Organizador;Âmbito"
    }

    @discardableResult func importCalendarText(_ raw: String) -> Int {
        let lines = raw.replacingOccurrences(of:"\r",with:"\n").components(separatedBy:"\n").map{$0.replacingOccurrences(of:"\\s+",with:" ",options:.regularExpression).trimmingCharacters(in:.whitespaces)}.filter{!$0.isEmpty}
        var out:[SwimMeet]=[]
        let dateRx = try! NSRegularExpression(pattern:"(?:\\d{1,2}(?:\\s*(?:e|a|–|-)\\s*\\d{1,2})?\\s+(?:jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-z.]*\\s+20\\d{2}|(?:jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-z.]*\\s+20\\d{2})",options:[.caseInsensitive])
        for line in lines {
            if line.contains(";") {
                let c=line.split(separator:";",omittingEmptySubsequences:false).map{String($0).trimmingCharacters(in:.whitespaces)}
                if c.count>=4 && !c[0].lowercased().contains("nome da prova") { let y=Int(c[2].firstMatch("20\\d{2}") ?? "0") ?? 0; out.append(.init(name:c[0],place:c[1],date:c[2],categories:c[3],organizer:c.count>4 ? c[4]:"",scope:c.count>5 ? c[5]:"",clubDivision:c[0].contains("3ª") || c[0].contains("3.ª") ? 3:nil,season:y>0 ? "\(y)/\(String((y+1)%100).leftPad2)":"")); continue }
            }
            let ns=line as NSString, rr=NSRange(location:0,length:ns.length)
            guard let dm=dateRx.firstMatch(in:line,range:rr) else { continue }
            let date=ns.substring(with:dm.range); let before=String(line.prefix(dm.range.location)).trimmingCharacters(in:.whitespaces); let after=String(line.dropFirst(dm.range.location+dm.range.length)).trimmingCharacters(in:.whitespaces)
            let words=before.split(separator:" "); guard words.count>=2 else{continue}; let place=String(words.last!); let name=words.dropLast().joined(separator:" ")
            let cats=["Juniores","Seniores","Absolutos","Absoluto","Juvenis","Infantis","Cadetes","Categorias"].filter{after.localizedCaseInsensitiveContains($0)}.joined(separator:", ")
            let y=Int(date.firstMatch("20\\d{2}") ?? "0") ?? 0
            out.append(.init(name:name,place:place,date:date,categories:cats.isEmpty ? after:cats,organizer:"",scope:after.localizedCaseInsensitiveContains("Nacional") ? "Nacional (FPN)":"",clubDivision:name.contains("3ª") || name.contains("3.ª") ? 3:nil,season:y>0 ? "\(y)/\(String((y+1)%100).leftPad2)":""))
        }
        if !out.isEmpty {
            let imported = Array(Dictionary(grouping:out,by:{"\($0.name)|\($0.date)"}).compactMap{$0.value.first}).map { m -> SwimMeet in var x=m; x.season=activeSeasonKey; return x }.sorted{$0.date<$1.date}
            calendarArchive[activeSeasonKey] = imported
            meets = imported
            save()
        }
        return out.count
    }

    func save() { if let p=try? JSONEncoder().encode(profile){defaults.set(p,forKey:"profile")}; if let x=try? JSONEncoder().encode(times){defaults.set(x,forKey:"times")}; if let x=try? JSONEncoder().encode(previous){defaults.set(x,forKey:"previous")}; defaults.set(tacs,forKey:"tacs"); if let x=try? JSONEncoder().encode(meets){defaults.set(x,forKey:"meets")}; if let x=try? JSONEncoder().encode(calendarArchive){defaults.set(x,forKey:"calendarArchive")} }
    private func load(){ if let d=defaults.data(forKey:"profile"),let x=try? JSONDecoder().decode(AthleteProfile.self,from:d){profile=x}; if let d=defaults.data(forKey:"times"),let x=try? JSONDecoder().decode([SwimTime].self,from:d){times=x}; if let d=defaults.data(forKey:"previous"),let x=try? JSONDecoder().decode([SwimTime].self,from:d){previous=x}; tacs=defaults.dictionary(forKey:"tacs") as? [String:String] ?? [:]; if let d=defaults.data(forKey:"calendarArchive"),let x=try? JSONDecoder().decode([String:[SwimMeet]].self,from:d){calendarArchive=x}; if calendarArchive.isEmpty, let d=defaults.data(forKey:"meets"),let x=try? JSONDecoder().decode([SwimMeet].self,from:d),!x.isEmpty{calendarArchive["2026/27"]=x} }
    private func normalizeStroke(_ v:String)->String { let x=v.folding(options:.diacriticInsensitive,locale:.current).lowercased(); if x.contains("liv"){return "Livres"}; if x.contains("cost"){return "Costas"}; if x.contains("bru"){return "Bruços"}; if x.contains("mar"){return "Mariposa"}; if x.contains("est"){return "Estilos"}; return "" }
    func normalizeEvent(_ v:String)->String { let d=v.firstMatch("\\d+") ?? ""; let s=normalizeStroke(v); return d.isEmpty||s.isEmpty ? "":"\(d) \(s)" }
    func normalizePool(_ v:String)->String { v.contains("25") ? "25m" : (v.contains("50") ? "50m":"") }
    func normalizeTime(_ v:String)->String { var x=v.trimmingCharacters(in:.whitespaces).replacingOccurrences(of:",",with:".").replacingOccurrences(of:" ",with:""); if x.hasPrefix("00:"){x=String(x.dropFirst(3))}; return x.range(of:"^\\d{1,2}(:\\d{2})?\\.\\d{2}$",options:.regularExpression) != nil ? x:"" }
    private func key(_ e:String,_ p:String)->String{"\(normalizePool(p))#\(normalizeEvent(e))"}
    private func selectBest(_ xs:[SwimTime])->[SwimTime] { Dictionary(grouping:xs,by:{$0.id}).compactMap{$0.value.min(by:{seconds($0.time)<seconds($1.time)})}.sorted{ a,b in if a.pool != b.pool { return a.pool=="25m" }; let so=["Livres":1,"Costas":2,"Bruços":3,"Mariposa":4,"Estilos":5]; let asv=so.first{$0.key == a.event.components(separatedBy:" ").dropFirst().joined(separator:" ")}?.value ?? 9; let bsv=so.first{$0.key == b.event.components(separatedBy:" ").dropFirst().joined(separator:" ")}?.value ?? 9; if asv != bsv{return asv<bsv}; return Int(a.event.firstMatch("\\d+") ?? "9999")! < Int(b.event.firstMatch("\\d+") ?? "9999")! } }
    private func seedTac()->[String:String] { var m:[String:String]=[:]; let a=[("50 Livres","28.71"),("100 Livres","1:01.62"),("200 Livres","2:12.93"),("400 Livres","4:36.72"),("800 Livres","9:18.62"),("1500 Livres","18:28.74"),("50 Costas","32.43"),("100 Costas","1:08.45"),("200 Costas","2:29.70"),("50 Bruços","36.23"),("100 Bruços","1:17.71"),("200 Bruços","2:48.61"),("50 Mariposa","30.62"),("100 Mariposa","1:07.50"),("200 Mariposa","2:30.99"),("100 Estilos","1:10.64"),("200 Estilos","2:30.08"),("400 Estilos","5:17.26")]; for (e,t) in a {m[key(e,"25m")]=t}; return m }
}

private extension String { func firstMatch(_ p:String)->String? { guard let r=range(of:p,options:.regularExpression) else{return nil}; return String(self[r]) } }

private extension String { var leftPad2:String { count>=2 ? self : "0"+self } }
