import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
 @EnvironmentObject var store: SwimStore
 @State private var tab=0
 @State private var pick=false
 var body: some View {
  TabView(selection:$tab) {
   AthleteView().tabItem{Label("Atleta",systemImage:"person.crop.circle")}.tag(0)
   ImportView(pick:$pick).tabItem{Label("Importar",systemImage:"square.and.arrow.down")}.tag(1)
   TimesView().tabItem{Label("Tempos",systemImage:"stopwatch")}.tag(2)
   TacView().tabItem{Label("TAC",systemImage:"checkmark.seal")}.tag(3)
   CalendarView().tabItem{Label("Calendário",systemImage:"calendar")}.tag(4)
   EvolutionView().tabItem{Label("Evolução",systemImage:"chart.line.uptrend.xyaxis")}.tag(5)
  }.tint(.cyan).preferredColorScheme(.dark)
  .fileImporter(isPresented:$pick,allowedContentTypes:[.pdf]) { result in if case .success(let u)=result { store.importPDF(u) } }
 }
}

struct AthleteView:View { @EnvironmentObject var s:SwimStore; @State var edit=false
 var body:some View { NavigationStack{ ScrollView{ VStack(spacing:14){ Text("SWIMTRACK").font(.largeTitle.bold()).foregroundStyle(.cyan); card("Nome",s.profile.name.isEmpty ? "Por importar":s.profile.name); card("Ano",s.profile.year); card("Género",s.profile.sex=="F" ? "Feminino":"Masculino"); card("Clube",s.profile.club); card("Época","\(s.profile.seasonStart)/\((Int(s.profile.seasonStart) ?? 2026)+1)"); card("Escalão",s.category); Button("Editar perfil"){edit=true}.buttonStyle(.borderedProminent) }.padding() }.navigationTitle("Atleta").sheet(isPresented:$edit){ProfileEditor()} } }
}
struct ImportView:View { @EnvironmentObject var s:SwimStore; @Binding var pick:Bool; @State var paste=""
 var body:some View{NavigationStack{ScrollView{VStack(spacing:16){
  VStack(spacing:8){Image(systemName:"wave.3.right.circle.fill").font(.system(size:54)).foregroundStyle(.cyan);Text("SWIMRANKINGS").font(.title.bold());Text("Importa o PDF oficial e o SwimTrack identifica o perfil e os melhores tempos.").multilineTextAlignment(.center).foregroundStyle(.secondary)}
  Button{pick=true}label:{Label("IMPORTAR PDF SWIMRANKINGS",systemImage:"doc.badge.plus").frame(maxWidth:.infinity)}.buttonStyle(.borderedProminent).controlSize(.large)
  VStack(alignment:.leading,spacing:8){Text("PERFIL DETETADO").font(.caption.bold()).foregroundStyle(.cyan);card("Atleta",s.profile.name);card("Clube",s.profile.club);card("Escalão",s.category);card("Época","\(s.profile.seasonStart)/\((Int(s.profile.seasonStart) ?? 2026)+1)");HStack{Label("\(s.times.filter{$0.pool=="25m"}.count) tempos 25 m",systemImage:"checkmark.circle");Spacer();Label("\(s.times.filter{$0.pool=="50m"}.count) tempos 50 m",systemImage:"checkmark.circle")}.font(.caption).foregroundStyle(.secondary)}.padding().background(Color.white.opacity(0.07)).clipShape(RoundedRectangle(cornerRadius:16))
  DisclosureGroup("Importação manual de texto"){TextEditor(text:$paste).frame(height:130).overlay(RoundedRectangle(cornerRadius:8).stroke(.gray));Button("Interpretar texto"){let n=s.importSwimrankingsText(paste);s.message="Importados \(n) tempos."}.buttonStyle(.bordered)}
  if !s.message.isEmpty{Text(s.message).foregroundStyle(.yellow).multilineTextAlignment(.center)}
 }.padding()}.navigationTitle("Swimrankings")}}

struct TimesView:View { @EnvironmentObject var s:SwimStore; @State var adding=false
 var body:some View{NavigationStack{List{ForEach(["25m","50m"],id:\.self){pool in Section(pool=="25m" ? "PISCINA CURTA 25m":"PISCINA LONGA 50m"){ForEach(s.times.filter{$0.pool==pool}){t in VStack(alignment:.leading){Text(t.event).bold();Text("\(t.time) • \(t.date) • \(t.city)");Text(s.status(t)).foregroundStyle(s.isQualified(t) ? .green:.red)}.swipeActions{Button(role:.destructive){s.deleteTime(t)}label:{Label("Apagar",systemImage:"trash")}}}}}}.navigationTitle("Tempos").toolbar{Button{adding=true}label:{Image(systemName:"plus")}}.sheet(isPresented:$adding){TimeEditor()}}}
struct TacView:View { @EnvironmentObject var s:SwimStore; @State var editing=false
 var body:some View{NavigationStack{List{Section("\(s.category)"){ForEach(s.times){t in let tac=s.tac(for:t); VStack(alignment:.leading){Text("\(t.event) • \(t.pool)").bold();Text("Tempo: \(t.time)   TAC: \(tac.isEmpty ? "por definir":tac)");Text(s.status(t)).foregroundStyle(s.isQualified(t) ? .green:.red)}}}}.navigationTitle("TAC").toolbar{Button("Editar"){editing=true}}.sheet(isPresented:$editing){TacEditor()}}}
struct CalendarView:View { @EnvironmentObject var s:SwimStore; @State private var onlyRelevant=true; @State private var pickCalendar=false
 var body:some View{NavigationStack{List{
  Section("ÉPOCA DESPORTIVA"){
   Picker("Época ativa",selection:Binding(get:{s.activeSeasonKey},set:{s.selectSeason($0)})){ForEach(s.availableSeasons,id:\.self){Text($0).tag($0)}}
   HStack{Text("Escalão");Spacer();Text(s.category).foregroundStyle(.secondary)}
   HStack{Text("Divisão do clube");Spacer();Text(s.profile.clubDivision.map{"\($0).ª Divisão"} ?? "Sem divisão").foregroundStyle(.secondary)}
  }
  Section{Toggle("Só competições aplicáveis",isOn:$onlyRelevant); Button{pickCalendar=true}label:{Label("IMPORTAR / ATUALIZAR CALENDÁRIO DE \(s.activeSeasonKey)",systemImage:"calendar.badge.plus")}} footer:{Text("Cada época fica guardada separadamente. Ao mudar para 2027/28, 2028/29 ou outra época, podes importar o respetivo calendário sem apagar os anos anteriores.")}
  Section("CALENDÁRIO \(s.activeSeasonKey)"){
   if s.meets.isEmpty { Text("Ainda não existe calendário para esta época. Importa o PDF/CSV oficial.").foregroundStyle(.secondary) }
   ForEach((onlyRelevant ? s.meets.filter{s.relevantMeet($0)} : s.meets)){m in
    VStack(alignment:.leading,spacing:5){HStack{Text(m.name).bold();Spacer();if s.relevantMeet(m){Image(systemName:"checkmark.seal.fill").foregroundStyle(.green)}};Text("\(m.date) • \(m.place)").foregroundStyle(.cyan);Text(m.categories).font(.subheadline);Text("\(m.scope) • \(m.organizer)").font(.caption).foregroundStyle(.secondary);if m.clubDivision == 3{Label("Competição de Clubes — 3.ª Divisão",systemImage:"person.3.fill").font(.caption.bold()).foregroundStyle(.yellow)}}.padding(.vertical,4)
   }
  }
 }.navigationTitle("Calendário").fileImporter(isPresented:$pickCalendar,allowedContentTypes:[.pdf,.commaSeparatedText,.plainText]){r in if case .success(let u)=r{s.importCalendar(u)}}}}

struct EvolutionView:View { @EnvironmentObject var s:SwimStore
 var body:some View{NavigationStack{List{if s.previous.isEmpty{Text("Importa um novo PDF depois de já existirem tempos guardados.")}else{ForEach(s.times){t in if let o=s.previous.first(where:{$0.id==t.id}){let d=s.seconds(t.time)-s.seconds(o.time);VStack(alignment:.leading){Text("\(t.event) • \(t.pool)").bold();Text("Anterior \(o.time) → Atual \(t.time)");Text(d<0 ? String(format:"Melhoria %.2f s",-d):String(format:"Diferença %.2f s",d)).foregroundStyle(d<=0 ? .green:.red)}}}}}.navigationTitle("Evolução")}}
struct ProfileEditor:View{@EnvironmentObject var s:SwimStore;@Environment(\.dismiss)var dismiss
 var body:some View{NavigationStack{Form{TextField("Nome",text:$s.profile.name);TextField("Ano",text:$s.profile.year);TextField("Clube",text:$s.profile.club);TextField("País",text:$s.profile.country);Picker("Género",selection:$s.profile.sex){Text("Feminino").tag("F");Text("Masculino").tag("M")};TextField("Ano inicial época",text:$s.profile.seasonStart);TextField("Escalão manual",text:$s.profile.categoryManual);TextField("ID Swimrankings",text:$s.profile.athleteId)}.navigationTitle("Editar perfil").toolbar{Button("Guardar"){s.save();dismiss()}}}}}
struct TimeEditor:View{@EnvironmentObject var s:SwimStore;@Environment(\.dismiss)var dismiss;@State var event="";@State var pool="25m";@State var time="";@State var date="-";@State var city="-"
 var body:some View{NavigationStack{Form{TextField("Prova: 100 Livres",text:$event);Picker("Piscina",selection:$pool){Text("25m").tag("25m");Text("50m").tag("50m")};TextField("Tempo",text:$time);TextField("Data",text:$date);TextField("Cidade",text:$city)}.navigationTitle("Inserir tempo").toolbar{Button("Guardar"){let e=s.normalizeEvent(event),t=s.normalizeTime(time);if !e.isEmpty && !t.isEmpty{s.upsertTime(.init(event:e,pool:pool,time:t,date:date,city:city,source:"Manual"));dismiss()}}}}}
struct TacEditor:View{@EnvironmentObject var s:SwimStore;@Environment(\.dismiss)var dismiss;@State var event="";@State var pool="25m";@State var time=""
 var body:some View{NavigationStack{Form{TextField("Prova: 100 Livres",text:$event);Picker("Piscina",selection:$pool){Text("25m").tag("25m");Text("50m").tag("50m")};TextField("TAC",text:$time)}.navigationTitle("Editar TAC").toolbar{Button("Guardar"){s.setTac(event:event,pool:pool,time:time);dismiss()}}}}}
func card(_ a:String,_ b:String)->some View{VStack(alignment:.leading,spacing:5){Text(a).font(.caption).foregroundStyle(.secondary);Text(b.isEmpty ? "Por definir":b).frame(maxWidth:.infinity,alignment:.leading)}.padding().background(Color.white.opacity(0.08)).clipShape(RoundedRectangle(cornerRadius:14))}
