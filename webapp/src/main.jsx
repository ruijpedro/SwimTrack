import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { jsPDF } from 'jspdf'
import './styles.css'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker

const STYLES = ['Livres', 'Costas', 'Bruços', 'Mariposa', 'Estilos']
const DEFAULT_PROFILE = { name: '', year: '', sex: 'F', club: '', country: 'Portugal', seasonStart: '2026', categoryManual: '', athleteId: '5631298' }
const DEFAULT_TAC = [
  ['25m','50 Livres','28.71'],['25m','100 Livres','1:01.62'],['25m','200 Livres','2:12.93'],['25m','400 Livres','4:36.72'],['25m','800 Livres','9:18.62'],['25m','1500 Livres','18:28.74'],
  ['25m','50 Costas','32.43'],['25m','100 Costas','1:08.45'],['25m','200 Costas','2:29.70'],['25m','50 Bruços','36.23'],['25m','100 Bruços','1:17.71'],['25m','200 Bruços','2:48.61'],
  ['25m','50 Mariposa','30.62'],['25m','100 Mariposa','1:07.50'],['25m','200 Mariposa','2:30.99'],['25m','100 Estilos','1:10.64'],['25m','200 Estilos','2:30.08'],['25m','400 Estilos','5:17.26']
].map(([pool,event,time])=>({pool,event,time,source:'FPN Júnior Feminino 1.º ano'}))

const clean = (s='') => s.trim().replace(/\s+/g,' ')
const timeToSec = (v='') => { const s=v.replace(',','.'); if(!s)return 1e9; const p=s.split(':').map(Number); return p.length===2?p[0]*60+p[1]:Number(s) }
const normalizeTime = (v='') => { let s=v.trim().replace(',','.').replace(/\s/g,''); if(s.startsWith('00:'))s=s.slice(3); return /^\d{1,2}(:\d{2})?\.\d{2}$/.test(s)?s:'' }
const normalizeStyle = (v='') => /liv/i.test(v)?'Livres':/cost/i.test(v)?'Costas':/bru/i.test(v)?'Bruços':/mar/i.test(v)?'Mariposa':/est/i.test(v)?'Estilos':''
const distanceOf = (e='') => Number(e.match(/\d+/)?.[0]||9999)
const eventKey = (x) => `${x.pool}#${x.event}`
const category = (profile) => {
  if(profile.categoryManual) return profile.categoryManual
  const year=Number(profile.year), season=Number(profile.seasonStart); if(!year||!season)return 'Por definir'
  const age=season-year, f=profile.sex==='F'
  if(f) return ({13:'Infantil B Feminino',14:'Infantil A Feminino',15:'Juvenil B Feminino',16:'Juvenil A Feminino',17:'Júnior Feminino 1.º ano',18:'Júnior Feminino 2.º ano'})[age] || (age>=19?'Sénior Feminino':'Por definir')
  return ({14:'Infantil B Masculino',15:'Infantil A Masculino',16:'Juvenil B Masculino',17:'Juvenil A Masculino',18:'Júnior Masculino 1.º ano',19:'Júnior Masculino 2.º ano'})[age] || (age>=20?'Sénior Masculino':'Por definir')
}
function parseSwimrankings(text){
  const lines=text.replace(/\r/g,'\n').split('\n').map(clean).filter(Boolean)
  let pool='', stroke=''; const results=[]
  const profileLine=lines.find(l=>/POR\s*-\s*Portugal/i.test(l))||''
  let profile={...DEFAULT_PROFILE}
  if(profileLine){
    const ym=profileLine.match(/\b(19|20)\d{2}\b/); const year=ym?.[0]||''
    const name=year?profileLine.substring(0,profileLine.indexOf(year)).replace(/^Software.*?Página \d+ de \d+/i,'').trim():''
    const club=profileLine.split(/POR\s*-\s*Portugal/i)[1]?.trim()||''
    profile={...profile,name,year,club,country:'Portugal',sex:/constanca/i.test(name)?'F':'F'}
  }
  const full=/^(Livres|Costas|Bruços|Brucos|Mariposa|Estilos)\s+(50|100|200|400|800|1500)m\s+(\d{1,2}:\d{2}[.,]\d{2}|\d{2}[.,]\d{2})\s+(\d{1,2}\s+[A-Za-zÀ-ÿ]{3}\s+\d{4})(?:\s+(.+))?$/i
  const normal=/^(50|100|200|400|800|1500)m\s+(\d{1,2}:\d{2}[.,]\d{2}|\d{2}[.,]\d{2})\s+(\d{1,2}\s+[A-Za-zÀ-ÿ]{3}\s+\d{4})(?:\s+(.+))?$/i
  const styleOnly=/^(Livres|Costas|Bruços|Brucos|Mariposa|Estilos)(?:\s+\d+m)?$/i
  for(const line of lines){
    if(/Piscina longa/i.test(line)){pool='50m';continue}
    if(/Piscina curta/i.test(line)){pool='25m';continue}
    let m=line.match(full)
    if(m){stroke=normalizeStyle(m[1]);results.push({event:`${m[2]} ${stroke}`,pool,time:normalizeTime(m[3]),date:m[4],city:m[5]||'-',source:'Swimrankings'});continue}
    m=line.match(normal)
    if(m&&stroke&&pool){results.push({event:`${m[1]} ${stroke}`,pool,time:normalizeTime(m[2]),date:m[3],city:m[4]||'-',source:'Swimrankings'});continue}
    m=line.match(styleOnly); if(m) stroke=normalizeStyle(m[1])
  }
  const best=[...new Map(results.filter(x=>x.time).sort((a,b)=>timeToSec(a.time)-timeToSec(b.time)).map(x=>[eventKey(x),x])).values()]
  return {profile,times:best}
}

function App(){
  const [tab,setTab]=useState('ATLETA')
  const [profile,setProfile]=useState(()=>JSON.parse(localStorage.getItem('swim-profile')||'null')||DEFAULT_PROFILE)
  const [times,setTimes]=useState(()=>JSON.parse(localStorage.getItem('swim-times')||'[]'))
  const [previous,setPrevious]=useState(()=>JSON.parse(localStorage.getItem('swim-previous')||'[]'))
  const [tacs,setTacs]=useState(()=>JSON.parse(localStorage.getItem('swim-tacs')||'null')||DEFAULT_TAC)
  const [filter,setFilter]=useState('ALL')
  useEffect(()=>localStorage.setItem('swim-profile',JSON.stringify(profile)),[profile])
  useEffect(()=>localStorage.setItem('swim-times',JSON.stringify(times)),[times])
  useEffect(()=>localStorage.setItem('swim-previous',JSON.stringify(previous)),[previous])
  useEffect(()=>localStorage.setItem('swim-tacs',JSON.stringify(tacs)),[tacs])
  const tacMap=useMemo(()=>Object.fromEntries(tacs.map(t=>[eventKey(t),t.time])),[tacs])
  const status=(x)=>{const tac=tacMap[eventKey(x)]; if(!tac)return {label:'TAC por definir',ok:null,diff:null}; const d=timeToSec(x.time)-timeToSec(tac); return d<=0?{label:`QUALIFICADA • margem ${(-d).toFixed(2)} s`,ok:true,diff:d}:{label:`Faltam ${d.toFixed(2)} s`,ok:false,diff:d}}
  const importPdf=async(file)=>{const data=await file.arrayBuffer(); const pdf=await pdfjsLib.getDocument({data}).promise; let text=''; for(let i=1;i<=pdf.numPages;i++){const page=await pdf.getPage(i); const c=await page.getTextContent(); text+=c.items.map(it=>it.str).join(' ')+'\n'} const parsed=parseSwimrankings(text); if(times.length)setPrevious(times); setTimes(parsed.times); setProfile(p=>({...p,...parsed.profile,seasonStart:p.seasonStart||'2026'})); alert(`Importados ${parsed.times.length} tempos.`)}
  const saveProfile=(e)=>{e.preventDefault(); const f=new FormData(e.currentTarget); setProfile(Object.fromEntries(f)); setTab('ATLETA')}
  const addTime=(e)=>{e.preventDefault(); const f=Object.fromEntries(new FormData(e.currentTarget)); const item={...f,time:normalizeTime(f.time),source:'Manual'}; if(!item.time)return alert('Tempo inválido'); setTimes(v=>[...v.filter(x=>eventKey(x)!==eventKey(item)),item]); setTab('TEMPOS')}
  const addTac=(e)=>{e.preventDefault(); const f=Object.fromEntries(new FormData(e.currentTarget)); const item={...f,time:normalizeTime(f.time),source:'Manual'}; if(!item.time)return alert('TAC inválido'); setTacs(v=>[...v.filter(x=>eventKey(x)!==eventKey(item)),item]); e.currentTarget.reset()}
  const exportPdf=()=>{const doc=new jsPDF(); let y=15; doc.setFontSize(18); doc.text('SWIMTRACK — RECORDES E TAC',14,y); y+=10; doc.setFontSize(10); [`Atleta: ${profile.name}`,`Clube: ${profile.club}`,`Escalão: ${category(profile)}`].forEach(s=>{doc.text(s,14,y);y+=6}); y+=3; times.forEach(x=>{if(y>280){doc.addPage();y=15} const tac=tacMap[eventKey(x)]||'-'; doc.text(`${x.event} ${x.pool} | ${x.time} | TAC ${tac} | ${status(x).label}`,14,y);y+=6}); doc.save('SwimTrack_Relatorio.pdf')}
  return <div className="app">
    <header><img src="./swimtrack-icon.png"/><h1>SWIMTRACK</h1><p>SWIMRANKINGS • TAC • EVOLUÇÃO</p><div className="summary">{profile.name||'SwimTrack'} • {times.length} tempos</div></header>
    <nav>{['ATLETA','IMPORTAR','TEMPOS','TAC','EVOLUÇÃO','MAIS'].map(t=><button className={tab===t?'active':''} onClick={()=>setTab(t)} key={t}>{t}</button>)}</nav>
    <main>
      {tab==='ATLETA'&&<section><h2>ATLETA</h2><Card title="Nome" body={profile.name||'Por importar'}/><Card title="Ano" body={profile.year||'Por importar'}/><Card title="Género" body={profile.sex==='F'?'Feminino':'Masculino'}/><Card title="Clube" body={profile.club||'Por importar'}/><Card title="País" body={profile.country}/><Card title="Época" body={`${profile.seasonStart}/${String(Number(profile.seasonStart)+1).slice(-2)}`}/><Card title="Escalão" body={category(profile)}/><button onClick={()=>setTab('EDITAR')}>✏️ EDITAR PERFIL</button></section>}
      {tab==='EDITAR'&&<ProfileForm profile={profile} onSubmit={saveProfile}/>} 
      {tab==='IMPORTAR'&&<section><h2>IMPORTAR</h2><Card title="PDF Swimrankings" body="Interpreta perfil, clube, género, escalão, piscina curta/longa e recordes De sempre."/><label className="file">📄 IMPORTAR PDF<input type="file" accept="application/pdf" onChange={e=>e.target.files[0]&&importPdf(e.target.files[0])}/></label><TimeForm onSubmit={addTime}/></section>}
      {tab==='TEMPOS'&&<section><h2>RECORDES PESSOAIS</h2>{['25m','50m'].map(pool=><Pool key={pool} pool={pool} times={times} status={status} tacMap={tacMap} setTimes={setTimes}/>)}</section>}
      {tab==='TAC'&&<section><h2>TAC</h2><div className="filters">{[['ALL','Todos'],['HAS','Tem TAC'],['OK','Qualificada'],['MISS','Por qualificar']].map(([v,l])=><button className={filter===v?'active':''} onClick={()=>setFilter(v)}>{l}</button>)}</div>{times.filter(x=>{const s=status(x); return filter==='ALL'||(filter==='HAS'&&s.ok!==null)||(filter==='OK'&&s.ok===true)||(filter==='MISS'&&s.ok===false)}).map(x=><StatusCard key={eventKey(x)} x={x} s={status(x)} tac={tacMap[eventKey(x)]}/>) }<h3>INSERIR/EDITAR TAC</h3><TacForm onSubmit={addTac}/></section>}
      {tab==='EVOLUÇÃO'&&<section><h2>EVOLUÇÃO</h2>{times.map(x=>{const old=previous.find(p=>eventKey(p)===eventKey(x)); const d=old?timeToSec(x.time)-timeToSec(old.time):null; return <Card key={eventKey(x)} title={`${x.event} ${x.pool}`} body={old?`Anterior: ${old.time}\nAtual: ${x.time}\nEvolução: ${d<=0?'melhoria':'pioria'} ${Math.abs(d).toFixed(2)} s`:'Sem histórico anterior'}/>})}</section>}
      {tab==='MAIS'&&<section><h2>MAIS</h2><button onClick={exportPdf}>📄 EXPORTAR PDF</button><button onClick={()=>{localStorage.clear();location.reload()}}>🗑 LIMPAR DADOS</button><Card title="Modo de utilização" body="WebApp instalável, dados guardados localmente no navegador."/></section>}
    </main>
  </div>
}
function Card({title,body,className=''}){return <div className={`card ${className}`}><strong>{title}</strong><span>{body}</span></div>}
function StatusCard({x,s,tac}){return <Card className={s.ok===true?'ok':s.ok===false?'bad':''} title={`${x.event} ${x.pool}`} body={`Tempo: ${x.time}\nTAC: ${tac||'-'}\n${s.label}`}/>} 
function ProfileForm({profile,onSubmit}){return <section><h2>EDITAR PERFIL</h2><form onSubmit={onSubmit}>{[['name','Nome'],['year','Ano de nascimento'],['club','Clube'],['country','País'],['sex','Género F/M'],['seasonStart','Ano inicial da época'],['categoryManual','Escalão manual'],['athleteId','ID Swimrankings']].map(([n,l])=><input name={n} placeholder={l} defaultValue={profile[n]}/>) }<button>💾 GUARDAR PERFIL</button></form></section>}
function TimeForm({onSubmit}){return <form onSubmit={onSubmit}><h3>INSERIR TEMPO MANUAL</h3><select name="pool"><option>25m</option><option>50m</option></select><input name="event" placeholder="Ex.: 100 Livres" required/><input name="time" placeholder="Ex.: 1:05.93" required/><input name="date" placeholder="Data"/><input name="city" placeholder="Cidade"/><button>➕ GUARDAR TEMPO</button></form>}
function TacForm({onSubmit}){return <form onSubmit={onSubmit}><select name="pool"><option>25m</option><option>50m</option></select><input name="event" placeholder="Ex.: 100 Livres" required/><input name="time" placeholder="TAC" required/><button>💾 GUARDAR TAC</button></form>}
function Pool({pool,times,status,tacMap,setTimes}){const rows=times.filter(x=>x.pool===pool);if(!rows.length)return null;return <div><h3>PISCINA {pool==='25m'?'CURTA':'LONGA'} {pool}</h3>{STYLES.map(s=>{const r=rows.filter(x=>x.event.includes(s)).sort((a,b)=>distanceOf(a.event)-distanceOf(b.event));return r.length?<div key={s}><h4>{s.toUpperCase()}</h4>{r.map(x=><div className="row" key={eventKey(x)}><StatusCard x={x} s={status(x)} tac={tacMap[eventKey(x)]}/><button onClick={()=>setTimes(v=>v.filter(z=>eventKey(z)!==eventKey(x)))}>Eliminar</button></div>)}</div>:null})}</div>}

createRoot(document.getElementById('root')).render(<App/>)
