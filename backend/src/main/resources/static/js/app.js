// operations and core AI features are retained; navigation is user-centered and remembers safe project/view context.
// ADMIN/MEMBER UI keeps account lifecycle, membership changes, TODO handoff, and duplicate review explicit.
// Operational changes are never hidden behind physical deletes or automatic task reassignment.
// Browser UI orchestration stays focused on view wiring; shared contracts/time/error normalization live in TypeScript runtime modules.
const csrfToken=document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader=document.querySelector('meta[name="_csrf_header"]')?.content;
let currentProject=null,currentUser=null,projects=[],todos=[],projectMembers=[],documentsCache=[],versionOptions=[];
let mediaRecorder=null,recordChunks=[],recordStartedAt=0,recordTimer=null,activeStream=null;
const {localDate,localMonth,localDateTimeWithOffset,errorMessage,normalizeAnalysis}=window.HubRuntime;

function headers(json=true){const h={};if(json)h['Content-Type']='application/json';if(csrfToken&&csrfHeader)h[csrfHeader]=csrfToken;return h;}
let refreshInFlight=null;
// The refresh token is single-use: if two requests 401 at the same instant and each fires its own
// refresh call, the loser presents an already-rotated token and the server treats that as a replay,
// revoking the whole session family even though the winner's refresh was legitimate. Sharing one
// in-flight promise means concurrent 401s collapse into a single refresh call, so this can't happen.
async function refreshSession(){
 if(refreshInFlight)return refreshInFlight;
 refreshInFlight=(async()=>{try{const r=await fetch('/api/auth/refresh',{method:'POST',headers:headers(false)});return r.ok;}finally{refreshInFlight=null;}})();
 return refreshInFlight;
}
async function api(url,opts={}){const retry=Boolean(opts._retried),request={...opts};delete request._retried;const method=(request.method||'GET').toUpperCase(),isForm=request.body instanceof FormData;if(method==='GET')request.cache='no-store';if(method!=='GET'&&method!=='HEAD')request.headers={...(request.headers||{}),...headers(!isForm&&request.body!==undefined)};const r=await fetch(url,request);if(r.status===401&&!retry&&!url.startsWith('/api/auth/')){if(await refreshSession())return api(url,{...opts,_retried:true});location.replace('/login');throw new Error('로그인이 필요합니다.');}if(!r.ok){const type=r.headers.get('content-type')||'';if(type.includes('json')){const body=await r.json();throw new Error(body.message||body.error||`HTTP ${r.status}`);}throw new Error((await r.text())||`HTTP ${r.status}`);}const type=r.headers.get('content-type')||'';return type.includes('json')?r.json():r.text();}

const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function renderJobStatus(job,label){const root=document.getElementById('jobStatusPanel');if(!root)return;root.hidden=false;root.replaceChildren(el('strong','',label||'AI가 자료를 확인하고 있습니다'),el('span','muted',` ${jobStatusLabel(job.status)} · ${job.progress||0}%`));if(job.errorMessage)root.appendChild(el('div','notice',`처리 중 문제가 생겼습니다. ${job.errorMessage}`));}
async function pollJob(jobId,label){const started=Date.now();while(Date.now()-started<10*60*1000){const job=await api(`/api/jobs/${jobId}`);renderJobStatus(job,label);if(job.status==='SUCCESS'){const root=document.getElementById('jobStatusPanel');if(root)setTimeout(()=>root.hidden=true,1200);return job;}if(job.status==='FAILED')throw new Error(job.errorMessage||'처리에 실패했습니다.');await delay(900);}throw new Error('처리 시간이 너무 길어졌습니다. 작업 목록에서 상태를 다시 확인해 주세요.');}
async function waitForJob(jobId,label){const job=await pollJob(jobId,label);if(!job.resultJson)return null;try{return normalizeAnalysis(JSON.parse(job.resultJson));}catch{return null;}}
async function waitForRawJob(jobId,label){const job=await pollJob(jobId,label);if(!job.resultJson)return null;try{return JSON.parse(job.resultJson);}catch{return null;}}
function flash(msg,ok=true){const x=document.getElementById('flash');x.hidden=false;x.textContent=msg;x.style.background=ok?'#eaf7ed':'#fdecec';x.style.color=ok?'#176a2f':'#9a1f1f';setTimeout(()=>x.hidden=true,4500);}
function el(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;}
function value(row,key){if(row==null)return null;return row[key]??row[key.toUpperCase()]??row[key.toLowerCase()];}
function safe(v){return v==null?'':String(v);}
function jobStatusLabel(status){return {PENDING:'준비 중',RUNNING:'처리 중',SUCCESS:'완료',FAILED:'처리 실패'}[status]||'처리 중';}
function searchMatchLabel(type){const raw=String(type||'').trim();if(/[가-힣]/.test(raw))return raw;return {RULE_EXACT:'관리자가 연결한 자료',RULE_PATTERN:'파일명 조건과 일치',EXACT:'파일명이 정확히 일치',TITLE:'제목과 일치',FILENAME:'파일명과 일치',SEMANTIC:'내용이 비슷한 자료',LEXICAL:'검색어가 포함된 자료',KEYWORD:'검색어가 포함된 자료',HYBRID:'여러 조건이 함께 맞는 자료'}[raw.toUpperCase()]||'관련 자료';}
function connectorName(type){return {GITHUB:'GitHub',GOOGLE_DRIVE:'Google Drive',SLACK:'Slack',NOTION:'Notion'}[type]||type||'연결 서비스';}
function connectorStatusLabel(status){return {SUCCESS:'가져오기 완료',FAILED:'가져오기 실패',PENDING:'가져오는 중',RUNNING:'가져오는 중'}[status]||'상태 확인 필요';}
function accountStatusLabel(status){return {ACTIVE:'사용 중',SUSPENDED:'사용 정지',WITHDRAWN:'탈퇴'}[status]||status||'상태 미정';}
function confidenceLabel(value){const v=String(value||'').toUpperCase();return {HIGH:'높음',MEDIUM:'보통',LOW:'낮음'}[v]||value||'확인 필요';}
function sourceTypeLabel(type){return {HUB:'직접 등록한 자료',FILE:'업로드 파일',MANUAL_TEXT:'직접 입력',LOCAL_PC:'내 PC 파일',MEETING:'회의 녹음',MEETING_TRANSCRIPT:'회의 녹음 기록',GOOGLE_DRIVE:'Google Drive',DRIVE_FILE:'Google Drive',SLACK:'Slack',SLACK_MESSAGE:'Slack 메시지',GITHUB:'GitHub',GIT_ISSUE:'GitHub 작업 항목',GIT_PR:'GitHub 변경 요청',GIT_COMMIT:'GitHub 변경 기록',NOTION:'Notion',NOTION_PAGE:'Notion 페이지'}[type]||'자료';}
function reviewStatusLabel(status){return {CONFIRMED:'확정됨',PENDING:'확인 전',REJECTED:'제외됨'}[status]||'확인 필요';}
function eventTypeLabel(type){return {DOCUMENT_IMPORTED:'자료 가져옴',DOCUMENT_UPDATED:'자료 새 내용 등록',DOCUMENT_ARCHIVE:'자료 보관',DOCUMENT_CHANGED:'자료 변경 확인',MEETING_UPLOADED:'회의 녹음 등록',MEETING_TRANSCRIBED:'회의 음성을 글로 변환',TODO_CREATED:'할 일 후보 생성',TODO_CONFIRMED:'할 일 확정',TODO_STATUS:'할 일 상태 변경',TODO_DUPLICATE_MERGED:'비슷한 할 일 내용 합침',DECISION_CANDIDATE:'결정 후보 생성',DECISION_CONFIRMED:'결정 확정',CONNECTOR_IMPORT:'연결 서비스 자료 가져옴',LOCAL_PC_IMPORT:'PC 자료 가져옴',PROJECT_MEMBER_JOIN:'프로젝트에 사람 추가',PROJECT_MEMBER_LEAVE:'프로젝트에서 사람 제외',PROJECT_MOVE:'프로젝트 이동',SEARCH_RULE_CREATE:'기준 자료 추가',SEARCH_RULE_UPDATE:'기준 자료 수정',SEARCH_RULE_DELETE:'기준 자료 삭제',USER_PROFILE_UPDATE:'프로필 수정',USER_ACCOUNT_STATUS:'사용 상태 변경',USER_ROLE_CHANGE:'관리자 여부 변경'}[type]||String(type||'기록').replaceAll('_',' ').toLowerCase();}
function entityTypeLabel(type){return {TODO:'할 일',DECISION:'결정',CHANGE_ITEM:'변경 내용',DOCUMENT:'자료',DOCUMENT_VERSION:'자료 버전',USER:'사용자',PROJECT:'프로젝트',SEARCH_RULE:'기준 자료 설정',MEETING:'회의'}[type]||'기록';}
function changeCategoryLabel(category){return {CONTENT:'내용 변경',SCHEDULE:'일정 변경',BUDGET:'예산 변경',SCOPE:'범위 변경',ASSIGNEE:'담당자 변경'}[String(category||'').toUpperCase()]||'내용 변경';}
function reassignmentReasonLabel(reason){return {ACCOUNT_WITHDRAWN:'담당자가 탈퇴함',ACCOUNT_SUSPENDED:'담당자 사용이 멈춤',PROJECT_MOVE:'담당자가 다른 프로젝트로 이동함',ADMIN_REMOVE:'담당자가 프로젝트에서 빠짐'}[reason]||'담당자 변경 필요';}

function operationDialog(title,renderBody){
 return new Promise(resolve=>{
  const dialog=document.createElement('dialog');dialog.className='operation-dialog';
  const form=document.createElement('form');form.method='dialog';form.className='stack';
  const heading=el('h3','',title),body=renderBody(),actions=el('div','row');
  const cancel=el('button','ghost','취소'),ok=el('button','','확인');cancel.type='button';ok.type='submit';
  cancel.onclick=()=>{dialog.close();resolve(null);};
  form.onsubmit=e=>{e.preventDefault();const result=body.getValue();dialog.close();resolve(result);};
  actions.append(cancel,ok);form.append(heading,body.node,actions);dialog.appendChild(form);document.body.appendChild(dialog);
  dialog.addEventListener('close',()=>dialog.remove(),{once:true});dialog.showModal();body.focus?.();
 });
}
function requestText(title,placeholder=''){
 return operationDialog(title,()=>{const input=document.createElement('input');input.placeholder=placeholder;input.maxLength=200;return{node:input,getValue:()=>input.value.trim()||null,focus:()=>input.focus()};});
}
function requestChoice(title,choices){
 return operationDialog(title,()=>{const select=document.createElement('select');choices.forEach(c=>{const o=document.createElement('option');o.value=String(c.value);o.textContent=c.label;select.appendChild(o);});return{node:select,getValue:()=>select.value||null,focus:()=>select.focus()};});
}
function requestManualEdit(currentTitle,currentText){
 return operationDialog('내용 수정',()=>{
  const node=el('div','stack');
  const titleInput=document.createElement('input');titleInput.placeholder='제목';titleInput.maxLength=500;titleInput.value=currentTitle||'';
  const textInput=document.createElement('textarea');textInput.placeholder='내용';textInput.value=currentText||'';textInput.rows=8;
  node.append(titleInput,textInput);
  return{node,getValue:()=>{const title=titleInput.value.trim(),text=textInput.value.trim();if(!title||!text)return null;return{title,text};},focus:()=>titleInput.focus()};
 });
}
// A plain <input type=file> shows its own filesystem path text with no way to restyle it, so the
// visible label lives in a sibling span instead and this just keeps that span in sync on change.
function bindFilePickerLabel(input){
 const label=input.closest('.file-picker');if(!label)return;const nameEl=label.querySelector('.file-picker-name');if(!nameEl)return;
 input.addEventListener('change',()=>{const files=[...(input.files||[])];nameEl.textContent=files.length?files.map(f=>f.name).join(', '):'선택된 파일이 없습니다.';});
}
function canConfirmCurrentProject(){return currentUser?.globalRole==='ADMIN'||Boolean(projects.find(p=>Number(p.id)===Number(currentProject))?.canConfirm);}
function viewAllowed(name){if(!document.getElementById(`view-${name}`))return false;if(name==='admin'&&currentUser?.globalRole!=='ADMIN')return false;if(name==='review'&&!canConfirmCurrentProject())return false;return true;}
function preferredInitialView(){const saved=sessionStorage.getItem('hub.lastView');if(saved&&viewAllowed(saved))return saved;return 'dashboard';}
function setProjectAvailability(available){const onboarding=document.getElementById('emptyProjectOnboarding');if(!onboarding)return;if(available){onboarding.hidden=true;document.querySelectorAll('.project-required').forEach(x=>x.removeAttribute('disabled'));return;}onboarding.hidden=false;const admin=currentUser?.globalRole==='ADMIN';document.getElementById('emptyProjectTitle').textContent=admin?'첫 프로젝트를 만들어 주세요':'프로젝트 배정을 기다리고 있습니다';document.getElementById('emptyProjectMessage').textContent=admin?'프로젝트를 만든 뒤 문서, 회의, 연결 서비스를 사용할 수 있습니다.':'관리자가 프로젝트를 만든 뒤 배정하면 문서, 회의, 검색 기능을 사용할 수 있습니다.';document.getElementById('emptyProjectCreate').hidden=!admin;document.querySelectorAll('.project-required').forEach(x=>x.setAttribute('disabled','disabled'));}
function requireCurrentProject(){if(currentProject)return true;flash(currentUser?.globalRole==='ADMIN'?'먼저 프로젝트를 만들어 주세요.':'아직 배정된 프로젝트가 없습니다. 관리자에게 요청해 주세요.',false);return false;}
function switchView(name){const target=viewAllowed(name)?name:'dashboard';if(target==='connectors')setTimeout(()=>void refreshConnectorStates(),0);document.querySelectorAll('.nav').forEach(x=>x.classList.toggle('active',x.dataset.view===target));document.querySelectorAll('.view').forEach(x=>x.classList.toggle('active',x.id===`view-${target}`));sessionStorage.setItem('hub.lastView',target);if(target==='admin')loadAdmin();}

const CONNECTOR_LABELS={GOOGLE_DRIVE:'Google Drive',GITHUB:'GitHub',SLACK:'Slack',NOTION:'Notion',EXTERNAL:'연결 서비스'};
/** Admin-off connector types stay hidden from the connectors screen entirely, not just blocked server-side. */
async function applyConnectorPolicy(){
 try{
  const policy=await api('/api/connector-policy');
  document.querySelectorAll('.connectorForm').forEach(node=>{node.hidden=policy[node.dataset.type]===false;});
 }catch{
  document.querySelectorAll('.connectorForm').forEach(node=>node.hidden=false);
 }
}
// The OAuth callback returns here with its outcome in the query string; without this the operator is sent
// back to a silent home screen and cannot tell whether the connection actually worked.
function reportConnectorResult(){
 const params=new URLSearchParams(location.search),status=params.get('status'),connector=params.get('connector');
 if(!status||!connector)return;
 const label=CONNECTOR_LABELS[connector]||connector,reason=params.get('reason');
 if(status==='connected')flash(`${label} 연결이 완료되었습니다.`);
 else if(reason==='access_denied')flash(`${label} 연결을 취소했습니다.`,false);
 else if(reason==='not_configured')flash(`${label} 개인 연결은 아직 설정되지 않았습니다. 관리자가 ${label} 앱 정보를 등록해야 합니다.`,false);
 else flash(`${label} 연결에 실패했습니다${reason?` (${reason})`:''}. 관리자 설정을 확인한 뒤 다시 시도해 주세요.`,false);
 history.replaceState(null,'',location.pathname);
}
function connectorBadge(data){
 if(!data)return el('span','link-badge unknown','상태 확인 중');
 if(data.linkedByUser)return el('span','link-badge linked',`연동됨 · ${data.account||'내 계정'}`);
 if(data.connected)return el('span','link-badge shared','서버 계정 사용 중');
 return el('span','link-badge off','연동 안 됨');
}
// The card must say whether it is linked before anything is clicked; loading the list is a separate,
// heavier action and an operator should not have to guess by pressing it.
async function refreshConnectorStates(){
 if(!currentProject)return;
 await Promise.all([...document.querySelectorAll('.connectorForm')].map(async form=>{
  const type=form.dataset.type;
  let data=null;
  try{data=await api(`/api/projects/${currentProject}/connectors/${type}/targets`);}catch{data={connected:false,linkedByUser:false};}
  form.dataset.targetCount=String((data.targets||[]).length);
  renderConnectorLink(form,type,data);
 }));
}
function renderConnectorLink(form,type,data){
 // Once this account has its own link, the "connect with account" link is dead weight next to the
 // 연결 해제 button below - hide it so the panel only ever offers the one action that applies.
 const connectLink=form.querySelector('.oauth-connect');if(connectLink)connectLink.hidden=Boolean(data.linkedByUser||data.connected);
 let box=form.querySelector('.connector-link-state');
 if(!box){box=el('div','connector-link-state');// the browse button now lives inside .connector-actions, so anchor to that direct child
  const anchor=form.querySelector('.connector-actions');if(anchor&&anchor.parentNode===form)form.insertBefore(box,anchor);else form.appendChild(box);}
 box.replaceChildren();
 box.appendChild(connectorBadge(data));
 const note=el('span','connector-link-note','');
 if(data.linkedByUser){
  note.textContent='이 계정 권한의 자료만 보입니다. 가져온 자료는 이 프로젝트의 검색에 함께 사용됩니다.';
  const off=el('button','ghost compact-button','연결 해제');off.type='button';
  off.onclick=async()=>{try{await api(`/api/projects/${currentProject}/connectors/${type}/link`,{method:'DELETE'});flash(`${connectorName(type)} 개인 연결을 해제했습니다.`);await refreshConnectorStates();}catch(err){flash(errorMessage(err),false);}};
  box.append(note,off);
 }else if(data.connected){
  note.textContent='본인 계정으로 보려면 위에서 연결하세요.';
  box.appendChild(note);
 }else{
  note.textContent='위에서 계정을 연결해 주세요.';
  box.appendChild(note);
 }
}
/** Folder-browser style popup: fetches this connector's targets and lets the user click one to pick it,
 * instead of scanning a flat <select> - a repo/folder/channel/page reads much more like a place to open. */
async function openConnectorBrowser(form){
 if(!form||!requireCurrentProject())return;
 const type=form.dataset.type,icon=form.dataset.icon||'📁',noun=form.dataset.noun||'항목';
 const scopeInput=form.querySelector('.connector-scope-value');
 const dialog=document.createElement('dialog');dialog.className='connector-browser-dialog';
 const head=el('div','connector-browser-head');
 head.append(el('span','connector-browser-icon',icon),el('strong','',`${connectorName(type)} ${noun} 찾아보기`));
 const refreshBtn=el('button','ghost compact-button','🔄 새로고침');refreshBtn.type='button';
 const closeBtn=el('button','ghost compact-button','✕');closeBtn.type='button';
 head.append(refreshBtn,closeBtn);
 const body=el('div','connector-browser-body');
 dialog.append(head,body);
 document.body.appendChild(dialog);
 closeBtn.onclick=()=>dialog.close();
 dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close();});
 dialog.addEventListener('close',()=>dialog.remove(),{once:true});
 const load=async()=>{
  body.replaceChildren(el('div','connector-browser-empty','불러오는 중...'));
  try{
   const data=await api(`/api/projects/${currentProject}/connectors/${type}/targets`);
   renderConnectorLink(form,type,data);
   body.replaceChildren();
   if(!data.connected){body.appendChild(el('div','connector-browser-empty','먼저 이 서비스를 연결해 주세요.'));return;}
   const list=data.targets||[];
   if(!list.length){body.appendChild(el('div','connector-browser-empty',`찾아볼 수 있는 ${noun}이(가) 없습니다.`));return;}
   list.forEach(t=>{
    const row=el('div','connector-browser-row');
    row.append(el('span','connector-browser-row-icon',icon));
    const text=el('span','connector-browser-row-text');
    text.append(el('strong','',t.name));if(t.description)text.append(el('small','',t.description));
    row.appendChild(text);
    const choose=el('button','ghost compact-button','선택');choose.type='button';
    choose.onclick=()=>{scopeInput.value=t.id;const browseBtn=form.querySelector('.connector-browse-btn');if(browseBtn)browseBtn.textContent=`${icon} ${t.name}`;dialog.close();};
    row.appendChild(choose);
    if(isSafeExternalUrl(t.url)){
      const open=el('a','ghost compact-button','열기 ↗');open.href=t.url;open.target='_blank';open.rel='noopener noreferrer';
      row.appendChild(open);
    }
    body.appendChild(row);
   });
  }catch(err){body.replaceChildren(el('div','connector-browser-empty',errorMessage(err)));}
 };
 refreshBtn.onclick=load;
 dialog.showModal();
 await load();
}
async function init(){
 currentUser=await api('/api/me');document.getElementById('meName').textContent=currentUser.displayName;
 document.body.classList.remove('auth-loading');const authLoading=document.getElementById('authLoadingScreen');if(authLoading)authLoading.hidden=true;
 bindNav();bindForms();
 document.getElementById('profileCompanyName').value=currentUser.companyName||'';document.getElementById('profileDepartmentName').value=currentUser.departmentName||'';document.getElementById('profileTeamName').value=currentUser.teamName||'';document.getElementById('profileJobTitle').value=currentUser.jobTitle||'';
 const accountLoginId=document.getElementById('accountLoginId'),accountEmail=document.getElementById('accountEmail'),accountRole=document.getElementById('accountRole'),accountStatus=document.getElementById('accountStatus');
 if(accountLoginId)accountLoginId.textContent=currentUser.loginId||'-';if(accountEmail)accountEmail.textContent=currentUser.email||'-';if(accountRole)accountRole.textContent=currentUser.globalRole==='ADMIN'?'관리자':'팀원';if(accountStatus)accountStatus.textContent=accountStatusLabel(currentUser.accountStatus);
 if(currentUser.mustChangePassword){document.getElementById('passwordChangeNotice').hidden=false;switchView('account');return;}
 // The violet "관리자" section label is only meaningful once at least one admin-only button sits
 // under it; left unconditional, a general user sees an empty violet-highlighted admin heading.
 const adminGroupLabel=document.querySelector('.nav-group-label-admin');
 await applyConnectorPolicy();
 if(currentUser.globalRole==='ADMIN'){document.getElementById('adminNav').hidden=false;document.getElementById('newProjectBtn').hidden=false;document.getElementById('renameProjectBtn').hidden=false;if(adminGroupLabel)adminGroupLabel.hidden=false;const dashOverview=document.getElementById('dashboardAdminOverview');if(dashOverview)dashOverview.hidden=false;const dashLink=document.getElementById('dashboardAdminLink');if(dashLink)dashLink.hidden=false;}else{document.getElementById('newProjectBtn').hidden=true;document.getElementById('renameProjectBtn').hidden=true;if(adminGroupLabel)adminGroupLabel.hidden=true;}
 await loadProjects();setProjectAvailability(Boolean(currentProject));
 // "담당자 배정" used to be admin-only; now anyone holding confirm-permission on at least one of
 // their own projects sees it too (each project's own grant still gates the screen's actual data).
 document.querySelectorAll('[data-view="review"]').forEach(node=>node.hidden=!(currentUser.globalRole==='ADMIN'||projects.some(p=>p.canConfirm)));
 document.getElementById('monthInput').value=localMonth();const documentMonthInput=document.getElementById('documentMonthInput');if(documentMonthInput)documentMonthInput.value=localMonth();renderRecentViews();if(currentProject)await refreshAll();if(currentUser.globalRole==='ADMIN')loadAdmin().catch(()=>{});switchView(preferredInitialView());reportConnectorResult();
}
async function loadProjects(){projects=await api('/api/projects');const sel=document.getElementById('projectSelect');sel.replaceChildren();projects.forEach(p=>{const o=document.createElement('option');o.value=p.id;o.textContent=p.name;sel.appendChild(o);});const savedProject=Number(sessionStorage.getItem('hub.projectId'));const candidate=projects.some(p=>Number(p.id)===Number(currentProject))?currentProject:(projects.some(p=>Number(p.id)===savedProject)?savedProject:projects[0]?.id||null);currentProject=candidate;sel.value=currentProject||'';if(currentProject)sessionStorage.setItem('hub.projectId',String(currentProject));sel.onchange=async()=>{currentProject=Number(sel.value);sessionStorage.setItem('hub.projectId',String(currentProject));await refreshAll();};}
function bindNav(){document.querySelectorAll('.nav').forEach(b=>b.onclick=()=>switchView(b.dataset.view));document.querySelectorAll('[data-open-view]').forEach(a=>a.onclick=e=>{e.preventDefault();switchView(a.dataset.openView);});}
function bindForms(){
 document.getElementById('emptyProjectCreate').onclick=async()=>{const name=await requestText('첫 프로젝트','프로젝트 이름');if(!name)return;try{await api('/api/projects',{method:'POST',body:JSON.stringify({name,description:''})});await loadProjects();setProjectAvailability(Boolean(currentProject));await refreshAll();flash('첫 프로젝트를 만들었습니다.');}catch(err){flash(errorMessage(err),false);}};
 document.getElementById('logoutBtn').onclick=async()=>{try{await api('/api/auth/logout',{method:'POST'});}finally{location.replace('/login');}};
 document.getElementById('newProjectBtn').onclick=async()=>{const name=await requestText('새 프로젝트','프로젝트 이름');if(!name)return;await api('/api/projects',{method:'POST',body:JSON.stringify({name,description:''})});await loadProjects();await refreshAll();};
 document.getElementById('renameProjectBtn').onclick=async()=>{
  if(!requireCurrentProject())return;
  const current=projects.find(p=>Number(p.id)===Number(currentProject));
  const name=await operationDialog('프로젝트 이름 수정',()=>{const input=document.createElement('input');input.maxLength=200;input.value=current?.name||'';return{node:input,getValue:()=>input.value.trim()||null,focus:()=>input.select()};});
  if(!name)return;
  try{await api(`/api/projects/${currentProject}`,{method:'PUT',body:JSON.stringify({name,description:current?.description||''})});flash('프로젝트 이름을 수정했습니다.');await loadProjects();}catch(err){flash(errorMessage(err),false);}
 };
 document.getElementById('monthInput').onchange=()=>{if(requireCurrentProject())loadTodos();};
 const reviewFilter=document.getElementById('reviewFilter');if(reviewFilter)reviewFilter.oninput=()=>{
  const q=reviewFilter.value.trim().toLowerCase();
  document.querySelectorAll('#reviewList .review-item').forEach(item=>{item.hidden=Boolean(q)&&!item.textContent.toLowerCase().includes(q);});
 };
 const documentFilter=document.getElementById('documentFilter');if(documentFilter)documentFilter.oninput=()=>{
  const q=documentFilter.value.trim().toLowerCase();
  document.querySelectorAll('#documentList .document-manage-card').forEach(item=>{item.hidden=Boolean(q)&&!item.textContent.toLowerCase().includes(q);});
 };
 document.getElementById('uploadForm').onsubmit=e=>handleDocumentUpload(e,document.getElementById('documentAnalysisResult'),'가져온 문서 요약');
 document.querySelectorAll('.file-picker input[type=file]').forEach(bindFilePickerLabel);
 const fileTransferForm=document.getElementById('fileTransferForm');if(fileTransferForm)fileTransferForm.onsubmit=async e=>{
  e.preventDefault();if(!requireCurrentProject())return;
  const form=e.target,file=selectedFile(form);
  try{
   validateClientFile(file,100*1024*1024,'파일');
   const fd=new FormData(form);
   await api(`/api/projects/${currentProject}/file-transfers?recipientId=${encodeURIComponent(fd.get('recipientId'))}&note=${encodeURIComponent(fd.get('note')||'')}`,{method:'POST',body:(()=>{const only=new FormData();only.append('file',file);return only;})()});
   flash('파일을 보냈습니다.');form.reset();await loadFileTransfers();
  }catch(err){flash(errorMessage(err),false);}
 };
 const manualMeetingForm=document.getElementById('manualMeetingForm');if(manualMeetingForm)manualMeetingForm.onsubmit=async e=>{
  e.preventDefault();if(!requireCurrentProject())return;const f=new FormData(e.target),title=String(f.get('title')||'').trim(),text=String(f.get('text')||'');
  const dueDate=f.get('dueDate')||null,assigneeId=f.get('assigneeId')?Number(f.get('assigneeId')):null;
  try{
   flash('입력한 내용을 정리하고 있습니다.');
   const d=await api(`/api/projects/${currentProject}/documents/manual`,{method:'POST',body:JSON.stringify({title,text,sourceDate:localDate(),dueDate,assigneeId})});
   const analysis=await waitForJob(d.jobId,'회의록 정리');
   renderAnalysisResult(document.getElementById('meetingResult'),analysis,'회의록 요약',{documentId:d.documentId,title,text});
   e.target.reset();flash(d.todoId?'회의록을 정리하고 할 일을 등록했습니다.':'회의록을 정리했습니다.');await refreshAll();
  }catch(err){flash(errorMessage(err),false);}
 };
 const quickManualForm=document.getElementById('quickManualForm');if(quickManualForm)quickManualForm.onsubmit=async e=>{e.preventDefault();const f=new FormData(e.target);const dueDate=f.get('dueDate')||null,assigneeId=f.get('assigneeId')?Number(f.get('assigneeId')):null;try{flash('회의 메모를 저장하고 할 일을 정리하는 중입니다.');const d=await api(`/api/projects/${currentProject}/documents/manual`,{method:'POST',body:JSON.stringify({title:f.get('title'),text:f.get('text'),sourceDate:localDate(),dueDate,assigneeId})});const analysis=await waitForJob(d.jobId,'회의 메모 정리');renderAnalysisResult(document.getElementById('quickManualResult'),analysis,'회의 메모 요약');e.target.reset();flash(d.todoId?'회의 메모를 정리하고 할 일을 등록했습니다.':'회의 메모를 정리했습니다. 확인할 할 일 후보가 있으면 아래에 표시됩니다.');await refreshAll();}catch(err){flash(errorMessage(err),false);}};
 const assigneeFilter=document.getElementById('todoAssigneeFilter'),statusFilter=document.getElementById('todoStatusFilter');if(assigneeFilter)assigneeFilter.onchange=renderTodoViews;if(statusFilter)statusFilter.onchange=renderTodoViews;
 document.getElementById('searchForm').onsubmit=async e=>{e.preventDefault();if(!requireCurrentProject())return;const q=document.getElementById('searchQuery').value.trim();if(!q)return;lastSearchQuery=q;try{await runSearchPage(q,0);await loadTopSearches();}catch(err){flash(err.message,false);}};
 const searchLoadMoreBtn=document.getElementById('searchLoadMore');
 if(searchLoadMoreBtn)searchLoadMoreBtn.onclick=async()=>{if(!lastSearchQuery)return;try{await runSearchPage(lastSearchQuery,searchLoadedCount);}catch(err){flash(err.message,false);}};
 document.getElementById('askForm').onsubmit=async e=>{e.preventDefault();if(!requireCurrentProject())return;const q=document.getElementById('askQuestion').value.trim();if(!q)return;try{const d=await api(`/api/projects/${currentProject}/materials/ask`,{method:'POST',body:JSON.stringify({question:q})});document.getElementById('ragAnswer').textContent=d.answer||'';renderMaterialResults(document.getElementById('ragSources'),d.sources||[],q);await loadTopSearches();}catch(err){flash(err.message,false);}};
 document.getElementById('contextForm').onsubmit=async e=>{e.preventDefault();await loadContext(document.getElementById('contextQuery').value.trim());};
 document.getElementById('compareForm').onsubmit=async e=>{e.preventDefault();const f=new FormData(e.target);try{const d=await api(`/api/projects/${currentProject}/changes`,{method:'POST',body:JSON.stringify({beforeVersionId:Number(f.get('beforeVersionId')),afterVersionId:Number(f.get('afterVersionId'))})});renderChanges(d.changes||[]);flash('업무 의미 변경 비교 완료');await loadReview();await loadTimeline();}catch(err){flash(err.message,false);}};
 document.querySelectorAll('.connector-browse-btn').forEach(btn=>btn.onclick=()=>openConnectorBrowser(btn.closest('.connectorForm')));
 document.querySelectorAll('.connectorForm').forEach(form=>form.onsubmit=async e=>{
  e.preventDefault();const f=new FormData(form),scope=f.get('scope');
  if(!scope){flash(`먼저 "찾아보기"로 ${form.dataset.noun||'항목'}을(를) 선택해 주세요.`,false);return;}
  const name=connectorName(form.dataset.type);
  try{
   const d=await api(`/api/projects/${currentProject}/connectors/${form.dataset.type}/import`,{method:'POST',body:JSON.stringify({scope})});
   const result=await waitForRawJob(d.jobId,`${name} 자료를 가져오는 중입니다`);
   flash(`${name} 자료 ${result?.imported??0}건을 가져왔습니다.`);
   await refreshAll();
  }catch(err){flash(err.message,false);}
 });
 const googleConnectBtn=document.getElementById('googleConnectBtn');if(googleConnectBtn)googleConnectBtn.onclick=e=>{e.preventDefault();if(!requireCurrentProject())return;location.href=`/api/projects/${currentProject}/connectors/google/authorize`;};
 // Google has its own dedicated button (googleConnectBtn, wired above) because its authorize route
 // doesn't follow the generic {type}/authorize pattern; it has no data-oauth-type and must stay out
 // of this generic loop, or this overwrites that handler with one that throws on click.
 document.querySelectorAll('.oauth-connect[data-oauth-type]').forEach(link=>link.onclick=e=>{e.preventDefault();if(!requireCurrentProject())return;location.href=`/api/projects/${currentProject}/connectors/${link.dataset.oauthType.toLowerCase()}/authorize`;});
 const searchRuleForm=document.getElementById('searchRuleForm');if(searchRuleForm)searchRuleForm.onsubmit=async e=>{e.preventDefault();if(!currentProject)return;const f=new FormData(e.target),ruleId=String(f.get('ruleId')||'').trim(),payload={name:String(f.get('name')||'').trim(),aliases:splitRuleValues(f.get('aliases')),patterns:splitRuleValues(f.get('patterns')),targetFile:String(f.get('targetFile')||'').trim()||null,mode:String(f.get('mode')||'SMART'),priority:Number(f.get('priority')||100),active:f.get('active')==='on'};try{await api(ruleId?`/api/admin/projects/${currentProject}/search/rules/${ruleId}`:`/api/admin/projects/${currentProject}/search/rules`,{method:ruleId?'PUT':'POST',body:JSON.stringify(payload)});flash('기준 자료를 저장했습니다. 파일 이름이 달라도 내용이 비슷한 자료를 함께 찾습니다.');resetSearchRuleForm();await loadSearchRules();}catch(err){flash(errorMessage(err),false);}};
 const searchRuleResetBtn=document.getElementById('searchRuleResetBtn');if(searchRuleResetBtn)searchRuleResetBtn.onclick=resetSearchRuleForm;
 const searchRuleTestForm=document.getElementById('searchRuleTestForm');if(searchRuleTestForm)searchRuleTestForm.onsubmit=async e=>{e.preventDefault();if(!currentProject)return;const q=String(new FormData(e.target).get('q')||'').trim();if(!q)return;try{const data=await api(`/api/admin/projects/${currentProject}/search/test?q=${encodeURIComponent(q)}`);renderSearchRuleTest(data);}catch(err){flash(errorMessage(err),false);}};
 const embeddingRetryBtn=document.getElementById('embeddingRetryBtn');if(embeddingRetryBtn)embeddingRetryBtn.onclick=async()=>{if(!currentProject)return;try{const data=await api(`/api/admin/projects/${currentProject}/search/embedding-retry`,{method:'POST',body:JSON.stringify({})});flash(`검색 준비를 다시 마쳤습니다 · ${data.reindexed||0}건`);await loadSearchRules();}catch(err){flash(errorMessage(err),false);}};
 const refreshProcessingJobsBtn=document.getElementById('refreshProcessingJobsBtn');if(refreshProcessingJobsBtn)refreshProcessingJobsBtn.onclick=loadProcessingJobs;
 const setTodoMode=mode=>{const list=document.getElementById('todoList'),calendar=document.getElementById('todoCalendar');list.style.display=mode==='calendar'?'none':'grid';list.classList.toggle('todo-grid',mode==='grid');calendar.style.display=mode==='calendar'?'grid':'none';document.getElementById('listMode').classList.toggle('active',mode==='list');document.getElementById('todoGridMode').classList.toggle('active',mode==='grid');document.getElementById('calendarMode').classList.toggle('active',mode==='calendar');if(mode==='calendar'){const [year,month]=String(document.getElementById('monthInput').value||localMonth()).split('-').map(Number);renderCalendar(year,month);}};
 document.getElementById('calendarMode').onclick=()=>setTodoMode('calendar');
 document.getElementById('listMode').onclick=()=>setTodoMode('list');
 document.getElementById('todoGridMode').onclick=()=>setTodoMode('grid');
 const docListMode=document.getElementById('docListMode'),docGridMode=document.getElementById('docGridMode'),docCalendarMode=document.getElementById('docCalendarMode'),documentMonthInput=document.getElementById('documentMonthInput');const setDocumentMode=mode=>{const list=document.getElementById('documentList'),calendar=document.getElementById('documentCalendar');list.style.display=mode==='calendar'?'none':'grid';list.classList.toggle('document-grid',mode==='grid');calendar.style.display=mode==='calendar'?'grid':'none';docListMode.classList.toggle('active',mode==='list');docGridMode.classList.toggle('active',mode==='grid');docCalendarMode.classList.toggle('active',mode==='calendar');if(mode==='calendar')renderDocumentCalendar();};if(docCalendarMode)docCalendarMode.onclick=()=>setDocumentMode('calendar');if(docListMode)docListMode.onclick=()=>setDocumentMode('list');if(docGridMode)docGridMode.onclick=()=>setDocumentMode('grid');if(documentMonthInput)documentMonthInput.onchange=renderDocumentCalendar;
 document.getElementById('viewerClose').onclick=()=>document.getElementById('viewer').hidden=true;
 document.getElementById('viewer').onclick=e=>{if(e.target.id==='viewer')e.currentTarget.hidden=true;};
 document.getElementById('memberRoleForm').onsubmit=async e=>{e.preventDefault();const f=new FormData(e.target);if(!f.get('userId')){flash('추가할 사람을 먼저 선택해 주세요.',false);return;}try{await api(`/api/admin/projects/${currentProject}/members`,{method:'PUT',body:JSON.stringify({userId:Number(f.get('userId'))})});flash('프로젝트에 팀원을 추가했습니다.');await loadMembers();await loadAdmin();}catch(err){flash(err.message,false);}};
 const sensitiveTermForm=document.getElementById('sensitiveTermForm');if(sensitiveTermForm)sensitiveTermForm.onsubmit=async e=>{e.preventDefault();const f=new FormData(e.target),term=String(f.get('term')||'').trim();if(!term)return;try{const d=await api('/api/admin/sensitive-terms',{method:'POST',body:JSON.stringify({term})});flash(d.status==='ALREADY_REGISTERED'?'이미 등록된 값입니다.':'해시 대상으로 추가했습니다. 새로 만들어지는 회의록부터 적용됩니다.');e.target.reset();await loadSensitiveTerms();}catch(err){flash(errorMessage(err),false);}};
 const copySignupLinkBtn=document.getElementById('copySignupLinkBtn');if(copySignupLinkBtn)copySignupLinkBtn.onclick=async()=>{const url=`${location.origin}/login#member`;try{if(navigator.clipboard?.writeText){await navigator.clipboard.writeText(url);flash('일반회원 가입 주소를 복사했습니다.');}else{flash(`가입 주소: ${url}`);}}catch{flash(`가입 주소: ${url}`);}};
 const profileForm=document.getElementById('profileForm');if(profileForm)profileForm.onsubmit=async e=>{e.preventDefault();const f=new FormData(e.target);try{currentUser=await api('/api/me/profile',{method:'PATCH',body:JSON.stringify({departmentName:f.get('departmentName')||'',teamName:f.get('teamName')||'',jobTitle:f.get('jobTitle')||''})});document.getElementById('profileCompanyName').value=currentUser.companyName||'';flash('프로필 저장 완료');}catch(err){flash(err.message,false);}};
 const passwordForm=document.getElementById('passwordForm');if(passwordForm){const next=passwordForm.elements.namedItem('newPassword'),confirm=passwordForm.elements.namedItem('newPasswordConfirm'),match=passwordForm.querySelector('.password-match'),update=()=>{if(!confirm||!match)return;const same=confirm.value&&next.value===confirm.value;match.textContent=!confirm.value?'':same?'비밀번호가 일치합니다.':'비밀번호가 일치하지 않습니다.';match.className=`password-match ${same?'available':'unavailable'}`;confirm.setCustomValidity(same?'':'비밀번호 확인이 일치하지 않습니다.')};next?.addEventListener('input',update);confirm?.addEventListener('input',update);passwordForm.onsubmit=async e=>{e.preventDefault();const f=new FormData(e.target);try{const d=await api('/api/auth/password',{method:'POST',body:JSON.stringify({currentPassword:f.get('currentPassword'),newPassword:f.get('newPassword')})});alert(d.message||'비밀번호가 변경되었습니다.');location.replace('/login');}catch(err){flash(err.message,false);}};}
 const withdrawForm=document.getElementById('withdrawForm');if(withdrawForm)withdrawForm.onsubmit=async e=>{e.preventDefault();if(!confirm('탈퇴하면 프로젝트 접근이 즉시 종료되고 되돌릴 수 없습니다. 계속할까요?'))return;const f=new FormData(e.target);try{const d=await api('/api/me/withdraw',{method:'POST',body:JSON.stringify({currentPassword:f.get('currentPassword'),reason:f.get('reason')||null})});alert(`탈퇴 처리 완료 · 재배정 필요 업무 ${d.reassignmentCount||0}건`);location.replace('/login');}catch(err){flash(errorMessage(err),false);}};
 const recordBtn=document.getElementById('recordBtn');if(recordBtn)recordBtn.onclick=startRecording;
 const stopBtn=document.getElementById('stopBtn');if(stopBtn)stopBtn.onclick=stopRecording;
 const audioUploadBtn=document.getElementById('audioUploadBtn');if(audioUploadBtn)audioUploadBtn.onclick=uploadAudioFile;
}


async function refreshAll(){if(!currentProject)return;await loadMembers();await Promise.all([loadTodos(),...(canConfirmCurrentProject()?[loadReview()]:[]),loadTimeline(),loadDocuments(),loadProcessingJobs(),loadTopSearches(),loadConnectorStatus(),loadFileTransfers()]);renderSummary();renderDashboardTodos();}
function memberDisplayName(userId){if(Number(currentUser?.id)===Number(userId))return `${currentUser.displayName||'나'} (나)`;const m=projectMembers.find(x=>Number(value(x,'user_id'))===Number(userId));return m?value(m,'display_name'):`사용자 #${userId}`;}
async function loadFileTransfers(){
 if(!currentProject)return;const root=document.getElementById('fileTransferList');if(!root)return;
 try{
  const rows=await api(`/api/projects/${currentProject}/file-transfers`);
  root.replaceChildren();
  if(!rows.length){root.appendChild(el('div','muted','주고받은 파일이 없습니다.'));return;}
  rows.forEach(r=>{
   const mine=Number(r.senderId)===Number(currentUser?.id);
   const item=el('div','item compact file-transfer-item');
   const who=mine?`${memberDisplayName(r.recipientId)}에게 보냄`:`${memberDisplayName(r.senderId)}이(가) 보냄`;
   item.appendChild(el('strong','',r.fileName));
   item.appendChild(el('div','muted',`${who} · ${(r.sizeBytes/1024).toFixed(0)}KB${r.todoId?' · 할 일에 첨부':''}${!mine&&!r.read?' · 안 읽음':''}`));
   if(r.note)item.appendChild(el('p','',r.note));
   const actions=el('div','row');
   const dl=document.createElement('a');dl.href=`/api/attachments/${r.id}/download`;dl.textContent='다운로드';dl.className='ghost';actions.appendChild(dl);
   if(mine){const del=el('button','ghost','삭제');del.type='button';del.onclick=async()=>{if(!confirm('이 파일을 지울까요?'))return;try{await api(`/api/attachments/${r.id}`,{method:'DELETE'});flash('파일을 지웠습니다.');await loadFileTransfers();}catch(err){flash(errorMessage(err),false);}};actions.appendChild(del);}
   item.appendChild(actions);root.appendChild(item);
  });
 }catch(err){root.replaceChildren(el('div','notice',errorMessage(err)));}
}
async function loadMembers(){
 projectMembers=currentProject?await api(`/api/projects/${currentProject}/members`):[];
 const filter=document.getElementById('todoAssigneeFilter');if(!filter)return;const prev=filter.value||'ALL';filter.replaceChildren();
 const all=document.createElement('option');all.value='ALL';all.textContent='전체 담당자';filter.appendChild(all);
 const mine=document.createElement('option');mine.value='ME';mine.textContent='내 할 일';filter.appendChild(mine);
 const none=document.createElement('option');none.value='UNASSIGNED';none.textContent='담당자 미정';filter.appendChild(none);
 projectMembers.forEach(m=>{const o=document.createElement('option');o.value=String(value(m,'user_id'));o.textContent=value(m,'display_name');filter.appendChild(o);});
 if([...filter.options].some(o=>o.value===prev))filter.value=prev;
 ['uploadAssignee','manualMeetingAssignee','quickManualAssignee'].forEach(id=>{
  const select=document.getElementById(id);if(!select)return;const previous=select.value;
  select.replaceChildren(new Option('지정 안 함',''));
  projectMembers.forEach(m=>select.appendChild(new Option(value(m,'display_name'),String(value(m,'user_id')))));
  if([...select.options].some(o=>o.value===previous))select.value=previous;
 });
 const recipientSelect=document.getElementById('fileTransferRecipient');
 if(recipientSelect){const previous=recipientSelect.value;recipientSelect.replaceChildren(new Option('받는 사람 선택',''));projectMembers.forEach(m=>recipientSelect.appendChild(new Option(value(m,'display_name'),String(value(m,'user_id')))));if([...recipientSelect.options].some(o=>o.value===previous))recipientSelect.value=previous;}
}
async function loadTopSearches(){if(!currentProject)return;const root=document.getElementById('topSearches');root.replaceChildren();try{const rows=await api(`/api/projects/${currentProject}/search/top`);if(!rows.length){root.appendChild(el('span','muted','아직 자주 찾는 검색어가 없습니다.'));return;}root.appendChild(el('span','muted','자주 찾는 검색어'));rows.forEach(row=>{const q=value(row,'query_text'),count=value(row,'search_count');const b=el('button','search-chip',`${q} · ${count}`);b.type='button';b.onclick=()=>{document.getElementById('searchQuery').value=q;document.getElementById('searchForm').requestSubmit();};root.appendChild(b);});}catch{}}
async function loadTodos(){const m=document.getElementById('monthInput').value||localMonth(),[y,mo]=m.split('-').map(Number),[monthRows,undatedRows]=await Promise.all([api(`/api/projects/${currentProject}/todos?year=${y}&month=${mo}`),api(`/api/projects/${currentProject}/todos/undated`)]);todos=[...monthRows,...undatedRows];renderTodoViews();renderSummary();renderDashboardTodos();}
function renderTodoViews(){const m=document.getElementById('monthInput').value||localMonth(),[y,mo]=m.split('-').map(Number);renderCalendar(y,mo);renderTodoList();renderTodoProgress(m);}
function renderTodoProgress(month){
 const root=document.getElementById('todoProgress');if(!root)return;
 const rows=todos.filter(t=>t.reviewStatus==='CONFIRMED'&&(!t.dueDate||String(t.dueDate).startsWith(month)));
 const total=rows.length,done=rows.filter(t=>t.taskStatus==='DONE').length,doing=rows.filter(t=>t.taskStatus==='IN_PROGRESS').length,waiting=rows.filter(t=>t.taskStatus==='TODO').length,blocked=rows.filter(t=>t.taskStatus==='BLOCKED').length,pct=total?Math.round(done*100/total):0;
 root.replaceChildren();
 const head=el('div','todo-progress-head');head.append(el('div','',`이번 달 진행 ${pct}%`),el('strong','',`${done}/${total||0} 완료`));
 const bar=el('div','todo-progress-track'),fill=el('div','todo-progress-fill');fill.style.width=`${pct}%`;bar.appendChild(fill);
 const stats=el('div','todo-progress-stats');[['진행 중',doing,'doing'],['시작 전',waiting,'waiting'],['도움 필요',blocked,'blocked']].forEach(([label,count,cls])=>{const x=el('div',`todo-stat ${cls}`);x.append(el('strong','',String(count)),el('span','',label));stats.appendChild(x);});
 root.append(head,bar,stats);
 const headerProgress=document.getElementById('headerProgress');if(headerProgress)headerProgress.textContent=`이번 달 진행 ${pct}% · ${done}/${total||0} 완료`;
 renderSearchTodoOverview(pct,done,total,doing,blocked);
}
function renderSearchTodoOverview(pct,done,total,doing,blocked){
 const root=document.getElementById('searchTodoOverview');if(!root)return;root.replaceChildren();
 const main=el('div','task-overview-main');main.append(el('span','eyebrow','현재 할 일'),el('strong','',total?`이번 달 ${total}건 중 ${done}건 완료`:'이번 달 배정된 할 일이 없습니다.'),el('span','muted',total?`진행 중 ${doing}건${blocked?` · 도움 필요한 일 ${blocked}건`:''}`:'자료를 검색하거나 회의록을 정리하면 할 일로 연결할 수 있습니다.'));
 const bar=el('div','task-overview-bar'),fill=el('i','');fill.style.width=`${pct}%`;bar.appendChild(fill);main.appendChild(bar);
 const btn=el('button','ghost','할 일 보기');btn.type='button';btn.onclick=()=>switchView('todos');root.append(main,btn);
}
function visibleTodos(){
 const assignee=document.getElementById('todoAssigneeFilter')?.value||'ALL',status=document.getElementById('todoStatusFilter')?.value||'ALL';
 return todos.filter(t=>{
  const assigneeOk=assignee==='ALL'||(assignee==='ME'&&Number(t.assigneeId)===Number(currentUser?.id))||(assignee==='UNASSIGNED'&&!t.assigneeId)||(Number(assignee)===Number(t.assigneeId));
  const statusOk=status==='ALL'||(status==='REVIEW'&&t.reviewStatus!=='CONFIRMED')||(status!=='REVIEW'&&t.taskStatus===status);
  return assigneeOk&&statusOk;
 });
}
function todoReadiness(t){
 if(t.taskStatus==='DONE')return {label:'완료',cls:'done'};
 if(t.taskStatus==='BLOCKED')return {label:'막힘',cls:'blocked'};
 if(t.assignmentStatus==='REASSIGNMENT_REQUIRED')return {label:'새 담당자 필요',cls:'review'};
 if(t.reviewStatus!=='CONFIRMED')return {label:'검토 필요',cls:'review'};
 if(t.assigneeId&&t.dueDate)return {label:'바로 실행',cls:'ready'};
 if(t.assigneeId)return {label:'기한 미정',cls:'missing'};
 return {label:'담당자 필요',cls:'missing'};
}
function statusPill(t){const r=todoReadiness(t);return el('span',`status-pill ${r.cls}`,r.label);}
function taskStatusLabel(status){return {TODO:'시작 전',IN_PROGRESS:'진행 중',DONE:'완료',BLOCKED:'도움 필요'}[status]||status;}
const CALENDAR_CHIP_LIMIT=3;
function renderCalendar(y,m){const cal=document.getElementById('todoCalendar'),undatedRoot=document.getElementById('todoUndated'),rows=visibleTodos();cal.className='calendar';cal.replaceChildren();undatedRoot.replaceChildren();const first=new Date(y,m-1,1),days=new Date(y,m,0).getDate();for(let i=0;i<first.getDay();i++)cal.appendChild(el('div','day'));for(let d=1;d<=days;d++){const day=el('div','day');day.appendChild(el('div','date',String(d)));const date=`${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`;const dayTodos=rows.filter(t=>t.dueDate===date);dayTodos.forEach((t,idx)=>{const chip=el('span','todo-chip',`${t.title} · ${taskStatusLabel(t.taskStatus)}`);chip.title=todoReadiness(t).label;if(idx>=CALENDAR_CHIP_LIMIT)chip.classList.add('day-chip-extra');day.appendChild(chip);});if(dayTodos.length>CALENDAR_CHIP_LIMIT){const extra=dayTodos.length-CALENDAR_CHIP_LIMIT,more=el('button','day-more',`+${extra}개 더보기`);more.type='button';more.onclick=()=>{const expanded=day.classList.toggle('day-expanded');more.textContent=expanded?'접기':`+${extra}개 더보기`;};day.appendChild(more);}cal.appendChild(day);}const undated=rows.filter(t=>!t.dueDate);if(undated.length){const box=el('div','panel');box.appendChild(el('h3','',`기한 미정 ${undated.length}건`));undated.forEach(t=>{const line=el('div','item');line.append(statusPill(t),el('span','',t.title));box.appendChild(line);});undatedRoot.appendChild(box);}}
function todoStagePercent(t){return t.taskStatus==='DONE'?100:t.taskStatus==='IN_PROGRESS'?55:t.taskStatus==='BLOCKED'?25:8;}
function relatedMaterialButton(todoId,label='관련 자료 보기'){const b=evidenceButton(`/api/todos/${todoId}/evidence`,label);b.classList.add('related-material-btn');return b;}
/** Not a menu: one click on the status badge moves forward. BLOCKED is a status you can filter/see (kept for future use) but isn't part of this quick cycle - a click from BLOCKED just re-enters it at TODO instead of dead-ending. */
const TASK_STATUS_CYCLE=['TODO','IN_PROGRESS','DONE'];
function nextTaskStatus(status){const idx=TASK_STATUS_CYCLE.indexOf(status);return idx===-1?TASK_STATUS_CYCLE[0]:TASK_STATUS_CYCLE[(idx+1)%TASK_STATUS_CYCLE.length];}
function buildTodoCard(t,isAdmin,muted){
 const item=el('article',`item todo-card${muted?' todo-card-muted':''}`);
 const top=el('div','todo-card-top'),titleWrap=el('div','todo-card-title'),side=el('div','todo-card-status');
 titleWrap.append(el('strong','',t.title));if(t.description)titleWrap.append(el('p','muted',t.description));side.appendChild(statusPill(t));top.append(titleWrap,side);item.appendChild(top);
 const facts=el('div','todo-facts');
 const assignee=el('div','todo-fact');assignee.append(el('span','','담당자'),el('strong','',t.assigneeText||t.assigneeSuggestionText||'아직 정하지 않음'));
 const due=el('div','todo-fact');due.append(el('span','','기한'),el('strong','',t.dueDate||t.dueDateSuggestion||'기한 미정'));
 facts.append(assignee,due);item.appendChild(facts);
 const progress=el('div','todo-stage'),stageHead=el('div','todo-stage-head');stageHead.append(el('span','','진행 상태'));const track=el('div','todo-stage-track'),fill=el('i','');fill.style.width=`${todoStagePercent(t)}%`;track.appendChild(fill);progress.append(stageHead,track);item.appendChild(progress);
 const actions=el('div','todo-card-actions');
 const statusBtn=el('button',`status-cycle-btn status-${(t.taskStatus||'TODO').toLowerCase()}`,taskStatusLabel(t.taskStatus));
 statusBtn.type='button';statusBtn.title='클릭하면 다음 상태로 바뀝니다 (메뉴 없이 계속 눌러 되돌릴 수 있어요)';
 statusBtn.disabled=t.assignmentStatus==='REASSIGNMENT_REQUIRED'||!(isAdmin||(t.reviewStatus==='CONFIRMED'&&Number(t.assigneeId)===Number(currentUser?.id)));
 statusBtn.onclick=async()=>{const next=nextTaskStatus(t.taskStatus);try{await api(`/api/todos/${t.id}/status`,{method:'PATCH',body:JSON.stringify({status:next})});await loadTodos();}catch(err){flash(errorMessage(err),false);}};
 const attachBtn=el('button','ghost compact-button','📎 첨부파일');attachBtn.type='button';
 const attachBox=el('div','todo-attachments');attachBox.hidden=true;
 attachBtn.onclick=async()=>{
  attachBox.hidden=!attachBox.hidden;
  if(!attachBox.hidden)await renderTodoAttachments(attachBox,t.id);
 };
 actions.append(statusBtn,relatedMaterialButton(t.id),attachBtn);item.append(actions,attachBox);
 return item;
}
function renderTodoList(){
 const root=document.getElementById('todoList');root.replaceChildren();const isAdmin=currentUser?.globalRole==='ADMIN',rows=visibleTodos();
 if(!rows.length){root.appendChild(el('div','dashboard-empty','현재 조건에 맞는 할 일이 없습니다.'));return;}
 // 기한 임박순 - 기한 없는 항목은 뒤로. 완료 항목은 목록에서 사라지지 않고 맨 아래로 밀리며, 오늘
 // 완료한 것만 회색으로 계속 보이고 그 전에 완료한 것은 접어 둔다 (주간보고·회고용 기록이라 삭제 금지).
 const sorted=[...rows].sort((a,b)=>{const ad=a.dueDate||'9999-12-31',bd=b.dueDate||'9999-12-31';return ad<bd?-1:ad>bd?1:0;});
 const today=localDate();
 const active=sorted.filter(t=>t.taskStatus!=='DONE');
 const doneToday=sorted.filter(t=>t.taskStatus==='DONE'&&String(t.updatedAt||'').slice(0,10)===today);
 const doneEarlier=sorted.filter(t=>t.taskStatus==='DONE'&&String(t.updatedAt||'').slice(0,10)!==today);
 active.forEach(t=>root.appendChild(buildTodoCard(t,isAdmin,false)));
 if(doneEarlier.length){
  const toggle=el('button','ghost todo-done-toggle',`완료 ${doneEarlier.length}건 더보기`);toggle.type='button';
  const box=el('div','todo-done-collapsed');box.hidden=true;
  doneEarlier.forEach(t=>box.appendChild(buildTodoCard(t,isAdmin,true)));
  toggle.onclick=()=>{box.hidden=!box.hidden;toggle.textContent=box.hidden?`완료 ${doneEarlier.length}건 더보기`:'완료한 지난 항목 접기';};
  root.append(toggle,box);
 }
 doneToday.forEach(t=>root.appendChild(buildTodoCard(t,isAdmin,true)));
}
async function renderTodoAttachments(box,todoId){
 box.replaceChildren(el('div','muted','불러오는 중...'));
 try{
  const rows=await api(`/api/todos/${todoId}/attachments`);
  box.replaceChildren();
  if(!rows.length)box.appendChild(el('div','muted','아직 첨부된 파일이 없습니다.'));
  rows.forEach(r=>{
   const row=el('div','row between');
   const dl=document.createElement('a');dl.href=`/api/attachments/${r.id}/download`;dl.textContent=`${r.fileName} (${(r.sizeBytes/1024).toFixed(0)}KB)`;
   row.appendChild(dl);box.appendChild(row);
  });
  const form=document.createElement('form');form.className='row';
  const label=el('label','file-picker compact-file-picker');
  const input=document.createElement('input');input.type='file';input.required=true;
  label.append(input,el('span','file-picker-btn','📎 파일 선택'),el('span','file-picker-name','선택된 파일이 없습니다.'));
  bindFilePickerLabel(input);
  const btn=el('button','ghost compact-button','추가');btn.type='submit';
  form.append(label,btn);
  form.onsubmit=async e=>{
   e.preventDefault();if(!input.files[0])return;
   try{
    validateClientFile(input.files[0],100*1024*1024,'파일');
    const fd=new FormData();fd.append('file',input.files[0]);
    await api(`/api/todos/${todoId}/attachments`,{method:'POST',body:fd});
    flash('파일을 첨부했습니다.');await renderTodoAttachments(box,todoId);
   }catch(err){flash(errorMessage(err),false);}
  };
  box.appendChild(form);
 }catch(err){box.replaceChildren(el('div','notice',errorMessage(err)));}
}
function renderDashboardTodos(){
 const root=document.getElementById('dashboardTodos');if(!root)return;root.replaceChildren();const isAdmin=currentUser?.globalRole==='ADMIN';let rows=todos.filter(t=>t.taskStatus!=='DONE');
 if(!isAdmin)rows=rows.filter(t=>t.reviewStatus==='CONFIRMED'&&Number(t.assigneeId)===Number(currentUser?.id));
 rows=rows.sort((a,b)=>String(a.dueDate||'9999-12-31').localeCompare(String(b.dueDate||'9999-12-31'))).slice(0,5);
 if(!rows.length){root.appendChild(el('div','dashboard-empty',isAdmin?'현재 확인하거나 배정할 할 일이 없습니다.':'현재 배정된 할 일이 없습니다.'));return;}
 rows.forEach(t=>{const item=el('div','item dashboard-todo'),main=el('div','todo-main'),side=el('div','dashboard-todo-side');main.append(el('div','todo-title',t.title),el('div','todo-sub',`담당 ${t.assigneeText||t.assigneeSuggestionText||'미정'} · ${t.dueDate||t.dueDateSuggestion||'기한 미정'}`));const mini=el('div','mini-progress'),fill=el('i','');fill.style.width=`${todoStagePercent(t)}%`;mini.appendChild(fill);main.appendChild(mini);const action=el('button','ghost',t.reviewStatus==='CONFIRMED'?'할 일 보기':'배정하기');action.type='button';action.onclick=()=>switchView(t.reviewStatus==='CONFIRMED'?'todos':'review');side.append(statusPill(t),action);item.append(main,side);root.appendChild(item);});
}
/**
 * One todo candidate's edit/assign/reject controls - shared by the 담당자 배정 review list and the
 * inline review that now appears right in the document/meeting analysis panel, so both places offer
 * the exact same actions instead of the inline one being a stripped-down copy.
 * opts.onChange runs after any action succeeds (default: refreshAll()); opts.onToggle, when given,
 * adds a bulk-select checkbox (used by the review list's bulk-assign bar; the inline panel omits it).
 */
function buildTodoReviewItem(t,opts={}){
 const change=opts.onChange||refreshAll;
 const item=el('div','item review-item');
 const top=el('div','row review-item-top');
 if(opts.onToggle){
  const check=document.createElement('input');check.type='checkbox';check.setAttribute('aria-label','일괄 배정용 선택');
  check.onchange=()=>opts.onToggle(t.id,check.checked);
  top.appendChild(check);
 }
 top.appendChild(statusPill(t));item.appendChild(top);
 const titleEl=el('strong','',t.title),descEl=el('p','',t.description||'');item.append(titleEl,descEl);
 if(t.possibleDuplicateOfId){const warn=el('div','duplicate-warning',`기존 할 일 #${t.possibleDuplicateOfId}와 제목이 비슷한 후보입니다. 자동 삭제하지 않고 관리자가 확인합니다.`);item.appendChild(warn);}
 item.appendChild(el('div','muted',`원문에 적힌 담당자: ${t.assigneeText||'없음'} · AI가 찾은 담당자 후보: ${t.assigneeSuggestionText||'없음'} · 예상 기한: ${t.dueDateSuggestion||'미정'} · AI가 찾은 내용의 확실함: ${confidenceLabel(t.confidence)}`));
 const row=el('div','review-actions'),assignee=document.createElement('select'),none=document.createElement('option');none.value='';none.textContent='담당자 미정';assignee.appendChild(none);
 projectMembers.forEach(m=>{const o=document.createElement('option'),id=value(m,'user_id');o.value=id;o.textContent=value(m,'display_name');if(Number(t.assigneeSuggestionId)===Number(id))o.selected=true;assignee.appendChild(o);});
 const due=document.createElement('input');due.type='date';due.value=t.dueDateSuggestion||localDate();
 const confirmBtn=el('button','', '이 사람에게 배정');confirmBtn.onclick=async()=>{if(!assignee.value){flash('업무 확정 전에 실제 팀원을 선택해 주세요.',false);return;}await api(`/api/todos/${t.id}/confirm`,{method:'POST',body:JSON.stringify({assigneeId:Number(assignee.value),dueDate:due.value||null})});flash('담당자와 기한을 정해 할 일을 배정했습니다.');await change();};
 const editBtn=el('button','ghost compact-button','✏️ 수정');editBtn.type='button';editBtn.onclick=async()=>{
  const result=await requestManualEdit(t.title,t.description||'');
  if(!result)return;
  try{await api(`/api/todos/${t.id}`,{method:'PATCH',body:JSON.stringify({title:result.title,description:result.text})});flash('할 일 후보 내용을 수정했습니다.');await change();}catch(err){flash(errorMessage(err),false);}
 };
 const reject=el('button','ghost','후보 제외');reject.onclick=async()=>{await api(`/api/todos/${t.id}/reject`,{method:'POST'});flash('할 일 후보를 제외했습니다.');await change();};
 row.append(assignee,due,confirmBtn,editBtn);
 if(t.possibleDuplicateOfId){const merge=el('button','ghost','기존 업무에 근거 합치기');merge.onclick=async()=>{await api(`/api/todos/${t.id}/merge-duplicate`,{method:'POST'});flash(`기존 할 일 #${t.possibleDuplicateOfId}에 근거를 합쳤습니다.`);await change();};row.appendChild(merge);}
 row.append(reject,relatedMaterialButton(t.id,'관련 자료 확인'));item.appendChild(row);
 return item;
}
/**
 * Inline version of the review list, scoped to just the todoIds a single analysis just produced -
 * lets someone extract, edit and assign a todo right where they uploaded the document/meeting
 * instead of navigating to 담당자 배정 for it. Only fetches anything when the viewer actually has
 * confirm permission (the same permission view-review itself requires); container just stays empty
 * otherwise, and requireConfirmPermission still guards every action server-side regardless.
 */
async function renderInlineTodoReview(container,todoIds){
 if(!todoIds?.length||!canConfirmCurrentProject()){container.replaceChildren();container.hidden=true;return;}
 let pending;
 try{pending=await api(`/api/projects/${currentProject}/review/todos`);}catch{container.hidden=true;return;}
 const mine=pending.filter(t=>todoIds.includes(Number(t.id)));
 container.replaceChildren();
 if(!mine.length){container.hidden=true;return;}
 container.hidden=false;
 container.appendChild(el('strong','',`할 일 후보 ${mine.length}건 - 바로 수정하고 담당자를 정하세요`));
 mine.forEach(t=>container.appendChild(buildTodoReviewItem(t,{onChange:async()=>{await refreshAll();await renderInlineTodoReview(container,todoIds);}})));
}
async function loadReview(){
 const root=document.getElementById('reviewList');root.replaceChildren();
 try{
  const [todoData,decisionData,changeData]=await Promise.all([api(`/api/projects/${currentProject}/review/todos`),api(`/api/projects/${currentProject}/review/decisions`),api(`/api/projects/${currentProject}/changes/review`)]);
  root.appendChild(el('h2','',`할 일 후보 ${todoData.length}건`));
  if(todoData.length&&!projectMembers.length)root.appendChild(el('div','panel muted','이 프로젝트에 배정할 팀원이 없습니다. 회사 관리에서 팀원을 프로젝트에 추가하면 담당자를 정할 수 있습니다.'));
  const selectedCandidates=new Set();
  if(todoData.length){
   const bulkBar=el('div','panel review-bulk-bar');
   const bulkAssignee=document.createElement('select'),bulkNone=document.createElement('option');bulkNone.value='';bulkNone.textContent='담당자 선택';bulkAssignee.appendChild(bulkNone);
   projectMembers.forEach(m=>{const o=document.createElement('option');o.value=value(m,'user_id');o.textContent=value(m,'display_name');bulkAssignee.appendChild(o);});
   const bulkDue=document.createElement('input');bulkDue.type='date';bulkDue.value=localDate();
   const bulkCount=el('span','muted','0건 선택됨');
   const bulkBtn=el('button','','선택한 항목 일괄 배정');bulkBtn.type='button';
   bulkBtn.onclick=async()=>{
    if(!selectedCandidates.size){flash('일괄 배정할 항목을 먼저 선택해 주세요.',false);return;}
    if(!bulkAssignee.value){flash('일괄 배정 전에 담당자를 선택해 주세요.',false);return;}
    try{
     const d=await api(`/api/projects/${currentProject}/review/todos/bulk-confirm`,{method:'POST',body:JSON.stringify({todoIds:[...selectedCandidates],assigneeId:Number(bulkAssignee.value),dueDate:bulkDue.value||null})});
     const failed=Object.values(d.results||{}).filter(v=>String(v).startsWith('FAILED')).length;
     flash(failed?`일괄 배정 완료 · 실패 ${failed}건은 개별 확인이 필요합니다.`:'선택한 항목을 모두 배정했습니다.',!failed);
     await refreshAll();
    }catch(err){flash(errorMessage(err),false);}
   };
   bulkBar.append(el('strong','','일괄 배정'),bulkAssignee,bulkDue,bulkBtn,bulkCount);
   root.appendChild(bulkBar);
  }
  todoData.forEach(t=>{
   const item=buildTodoReviewItem(t,{onToggle:(id,checked)=>{
    if(checked)selectedCandidates.add(id);else selectedCandidates.delete(id);
    const bulkCountEl=root.querySelector('.review-bulk-bar .muted');if(bulkCountEl)bulkCountEl.textContent=`${selectedCandidates.size}건 선택됨`;
   }});
   root.appendChild(item);
  });
  root.appendChild(el('h2','',`결정 후보 ${decisionData.length}건`));decisionData.forEach(d=>{const item=el('div','item review-item'),id=value(d,'id');item.appendChild(el('strong','',value(d,'statement')));item.appendChild(el('div','muted',`AI 판단 신뢰도 ${confidenceLabel(value(d,'confidence'))}`));const b=el('button','', '결정 확정');b.onclick=async()=>{await api(`/api/decisions/${id}/confirm`,{method:'POST'});flash('결정을 확정했습니다.');await refreshAll();};const r=el('button','ghost','후보 제외');r.onclick=async()=>{await api(`/api/decisions/${id}/reject`,{method:'POST'});flash('결정 후보를 제외했습니다.');await refreshAll();};item.append(b,r,evidenceButton(`/api/decisions/${id}/evidence`));root.appendChild(item);});
  root.appendChild(el('h2','',`변경 후보 ${changeData.length}건`));changeData.forEach(c=>{const item=el('div','item review-item'),id=value(c,'id');item.appendChild(el('strong','',value(c,'category')||'CONTENT'));item.appendChild(el('p','',`${value(c,'before_text')||''} → ${value(c,'after_text')||''}`));const b=el('button','', '변경 확정');b.onclick=async()=>{await api(`/api/projects/${currentProject}/changes/items/${id}/confirm`,{method:'POST'});flash('변경 사항을 확정했습니다.');await refreshAll();};const r=el('button','ghost','후보 제외');r.onclick=async()=>{await api(`/api/projects/${currentProject}/changes/items/${id}/reject`,{method:'POST'});flash('변경 후보를 제외했습니다.');await refreshAll();};item.append(b,r,evidenceButton(`/api/projects/${currentProject}/changes/items/${id}/evidence`));root.appendChild(item);});
  document.getElementById('reviewFilter')?.dispatchEvent(new Event('input'));
 }catch{root.textContent='관리자만 AI 후보를 검토·확정할 수 있습니다.';}
}
function evidenceButton(url,label='근거 원문'){const b=el('button','ghost',label);b.type='button';b.onclick=()=>showEvidence(url);return b;}

const WORKFLOW_COLUMNS=[
 {key:'DOCS',label:'문서 · 회의',match:t=>t.startsWith('DOCUMENT_')||t.startsWith('MEETING_')},
 {key:'TASKS',label:'할 일 · 결정',match:t=>t.startsWith('TODO_')||t.startsWith('DECISION_')},
 {key:'SYNC',label:'연결 · 가져오기',match:t=>t.startsWith('CONNECTOR_')||t==='LOCAL_PC_IMPORT'},
 {key:'TEAM',label:'팀 · 계정',match:t=>t.startsWith('PROJECT_MEMBER_')||t==='PROJECT_MOVE'||t.startsWith('USER_')},
 {key:'SEARCH',label:'검색 기준',match:t=>t.startsWith('SEARCH_RULE_')},
 {key:'ETC',label:'기타',match:()=>true}
];
const WORKFLOW_COLUMN_LIMIT=4;
function loadTimelineFallbackCategory(type){return WORKFLOW_COLUMNS.find(c=>c.match(String(type||'')))||WORKFLOW_COLUMNS[WORKFLOW_COLUMNS.length-1];}
async function loadTimeline(){
 const data=await api(`/api/projects/${currentProject}/timeline`);
 const root=document.getElementById('dashboardTimeline');if(!root)return;root.replaceChildren();
 root.classList.add('workflow-columns');
 if(!data.length){root.appendChild(el('div','dashboard-empty','아직 기록된 업무 흐름이 없습니다.'));return;}
 const byCategory=new Map();
 data.slice(0,200).forEach(x=>{const cat=loadTimelineFallbackCategory(x.eventType);if(!byCategory.has(cat.key))byCategory.set(cat.key,{label:cat.label,items:[]});byCategory.get(cat.key).items.push(x);});
 WORKFLOW_COLUMNS.forEach(cat=>{
  const group=byCategory.get(cat.key);if(!group||!group.items.length)return;
  const col=el('div','workflow-column');
  col.appendChild(el('div','workflow-column-head',`${group.label} · ${group.items.length}건`));
  group.items.forEach((x,idx)=>{
   const i=el('div','item compact');if(idx>=WORKFLOW_COLUMN_LIMIT)i.classList.add('workflow-item-extra');
   i.appendChild(el('strong','',x.title));i.appendChild(el('div','muted',`${eventTypeLabel(x.eventType)} · ${x.happenedAt}`));
   if(x.description)i.appendChild(el('p','',x.description));
   col.appendChild(i);
  });
  if(group.items.length>WORKFLOW_COLUMN_LIMIT){
   const extra=group.items.length-WORKFLOW_COLUMN_LIMIT,more=el('button','workflow-more',`+${extra}개 더보기`);more.type='button';
   more.onclick=()=>{const expanded=col.classList.toggle('workflow-expanded');more.textContent=expanded?'접기':`+${extra}개 더보기`;};
   col.appendChild(more);
  }
  root.appendChild(col);
 });
}
async function loadDocuments(){
 documentsCache=await api(`/api/projects/${currentProject}/documents`);const root=document.getElementById('documentList');root.replaceChildren();versionOptions=[];
 if(!documentsCache.length){root.appendChild(el('div','panel muted','아직 등록된 문서가 없습니다.'));populateCompareSelectors();return;}
 // Fetch every document's versions in parallel instead of one await per document in sequence -
 // with hundreds of documents, a sequential loop turned this page load into a multi-second wait.
 const versionsByDoc=await Promise.all(documentsCache.map(d=>api(`/api/documents/${value(d,'id')}/versions`)));
 documentsCache.forEach((d,docIndex)=>{
  const id=value(d,'id'),i=el('div','item document-manage-card'),name=value(d,'original_name'),archived=Boolean(value(d,'archived'));
  i.appendChild(el('strong','',name));i.appendChild(el('div','muted',`${sourceTypeLabel(value(d,'source_type'))} · 최신 자료 ${value(d,'latest_version')||'-'}${value(d,'source_deleted')?' · 원본에서 삭제됨':''}${archived?' · 보관됨':''}`));
  const versions=versionsByDoc[docIndex];versions.forEach(v=>versionOptions.push({id:value(v,'id'),label:`${name} · ${value(v,'version_no')}번째 저장본`,documentId:id}));
  if(versions.length){
   const actions=el('div','row document-actions'),latestId=value(versions[0],'id'),detail=el('button','ghost','원문 / 요약 보기');detail.type='button';detail.onclick=()=>openVersion(latestId);actions.append(detail);
   if(value(d,'source_type')==='MANUAL_TEXT'){const edit=el('button','ghost','✏️ 수정');edit.type='button';edit.onclick=async()=>{
    const current=await api(`/api/versions/${latestId}`);const result=await requestManualEdit(name,value(current,'full_text'));if(!result)return;
    try{flash('수정한 내용을 다시 정리하고 있습니다.');const r=await api(`/api/projects/${currentProject}/documents/${id}`,{method:'PUT',body:JSON.stringify(result)});await waitForJob(r.jobId,'수정 내용 정리');flash('수정한 내용을 반영했습니다.');await refreshAll();}catch(err){flash(errorMessage(err),false);}
   };actions.appendChild(edit);}
   if(currentUser?.globalRole==='ADMIN'&&!archived){const archive=el('button','ghost','보관');archive.type='button';archive.onclick=async()=>{if(!confirm(`'${name}' 문서를 보관할까요?\n원본 이력은 삭제하지 않고 검색/업무 기록용 이력은 유지합니다.`))return;try{await api(`/api/documents/${id}`,{method:'DELETE'});flash('문서를 보관했습니다.');await refreshAll();}catch(err){flash(errorMessage(err),false);}};actions.appendChild(archive);}
   i.appendChild(actions);
   if(versions.length>1){const history=document.createElement('details');history.className='document-version-history';const summary=document.createElement('summary');summary.textContent=`이전 저장본 ${versions.length}개 보기`;history.appendChild(summary);versions.forEach(v=>{const row=el('div','row between version-history-row'),label=el('span','',`${value(v,'version_no')}번째 저장본`),open=el('button','ghost','보기');open.type='button';open.onclick=()=>openVersion(value(v,'id'));row.append(label,open);history.appendChild(row);});i.appendChild(history);}
  }
  root.appendChild(i);
 });
 populateCompareSelectors();renderDocumentCalendar();
 document.getElementById('documentFilter')?.dispatchEvent(new Event('input'));
}
function renderDocumentCalendar(){const cal=document.getElementById('documentCalendar');if(!cal)return;const input=document.getElementById('documentMonthInput'),m=input?.value||localMonth(),[y,mo]=m.split('-').map(Number);cal.replaceChildren();const first=new Date(y,mo-1,1),days=new Date(y,mo,0).getDate();for(let i=0;i<first.getDay();i++)cal.appendChild(el('div','day'));for(let d=1;d<=days;d++){const day=el('div','day');day.appendChild(el('div','date',String(d)));const date=`${y}-${String(mo).padStart(2,'0')}-${String(d).padStart(2,'0')}`;const dayDocs=documentsCache.filter(x=>String(value(x,'created_at')||'').slice(0,10)===date);dayDocs.forEach((x,idx)=>{const chip=el('button','doc-chip',value(x,'original_name'));chip.type='button';chip.title=sourceTypeLabel(value(x,'source_type'));if(idx>=CALENDAR_CHIP_LIMIT)chip.classList.add('day-chip-extra');chip.onclick=async()=>{const versions=await api(`/api/documents/${value(x,'id')}/versions`);if(versions.length)openVersion(value(versions[0],'id'));};day.appendChild(chip);});if(dayDocs.length>CALENDAR_CHIP_LIMIT){const extra=dayDocs.length-CALENDAR_CHIP_LIMIT,more=el('button','day-more',`+${extra}개 더보기`);more.type='button';more.onclick=()=>{const expanded=day.classList.toggle('day-expanded');more.textContent=expanded?'접기':`+${extra}개 더보기`;};day.appendChild(more);}cal.appendChild(day);}}
async function loadProcessingJobs(){
 const root=document.getElementById('processingJobList');if(!root||!currentProject)return;root.replaceChildren();
 try{const rows=await api(`/api/projects/${currentProject}/jobs`);if(!rows.length){root.appendChild(el('div','muted','최근 AI 처리 기록이 없습니다.'));return;}rows.slice(0,20).forEach(job=>{const item=el('div','item compact job-history-item'),kind={DOCUMENT_ANALYZE:'문서 정리',MEETING_ANALYZE:'회의 정리'}[job.jobType]||String(job.jobType||'AI 처리').replaceAll('_',' ');item.appendChild(el('strong','',`${kind} · ${jobStatusLabel(job.status)}`));item.appendChild(el('div','muted',`진행 ${job.progress||0}% · ${job.updatedAt?new Date(job.updatedAt).toLocaleString('ko-KR'):''}`));if(job.errorMessage)item.appendChild(el('div','notice',job.errorMessage));root.appendChild(item);});}catch(err){root.appendChild(el('div','notice',errorMessage(err)));}
}
function populateCompareSelectors(){for(const id of ['beforeVersionSelect','afterVersionSelect']){const sel=document.getElementById(id);sel.replaceChildren();const placeholder=document.createElement('option');placeholder.value='';placeholder.textContent=id.startsWith('before')?'이전 자료 선택':'현재 자료 선택';sel.appendChild(placeholder);versionOptions.forEach(v=>{const o=document.createElement('option');o.value=v.id;o.textContent=v.label;sel.appendChild(o);});}}
async function openVersion(versionId){const d=await api(`/api/versions/${versionId}`);const title=value(d,'original_name'),meta=`${sourceTypeLabel(value(d,'source_type'))} · ${value(d,'version_no')}번째 저장본`;openViewer({title,meta,evidence:'문서 전체 보기',summary:value(d,'summary')||'요약 없음',text:value(d,'full_text')||''});recordRecentView({type:'version',id:versionId,title,meta});}
function renderChanges(items){const root=document.getElementById('changeResults');root.replaceChildren();items.forEach(c=>{const i=el('div','item');i.appendChild(el('strong','',changeCategoryLabel(c.category)));i.appendChild(el('p','',`${c.before||''} → ${c.after||''}`));if(c.reason)i.appendChild(el('div','muted',c.reason));root.appendChild(i);});}

async function showEvidence(url){try{const rows=await api(url);if(!rows.length){flash('연결된 근거가 없습니다.',false);return;}const first=rows[0];let detail=null;if(first.versionId)detail=await api(`/api/versions/${first.versionId}`);else if(first.chunkId)detail=await api(`/api/chunks/${first.chunkId}`);const evidence=rows.map(x=>x.documentName?`${x.documentName} ${x.paragraphRef||''}${x.pageNo?` · ${x.pageNo}페이지`:''}\n${x.quote}`:`${x.meetingTitle||'회의'} · ${formatMs(x.startMs)}-${formatMs(x.endMs)}${x.speaker?` · ${x.speaker}`:''}\n${x.quote}`).join('\n\n');openViewer({title:first.documentName||first.meetingTitle||'근거',meta:first.documentName?'문서에서 확인한 근거':'회의 녹음에서 확인한 근거',evidence,summary:detail?value(detail,'summary')||'요약 없음':'회의 원문은 녹음 결과와 해당 시간 위치에서 확인할 수 있습니다.',text:detail?value(detail,'full_text')||'':'',quote:first.quote});}catch(err){flash(err.message,false);}}
function openViewer({title,meta,evidence,summary,text,quote}){document.getElementById('viewerTitle').textContent=title||'원문 확인';document.getElementById('viewerMeta').textContent=meta||'';document.getElementById('viewerEvidence').textContent=evidence||'';document.getElementById('viewerSummary').textContent=summary||'';const pre=document.getElementById('viewerText');pre.textContent=text||'전체 원문이 저장되지 않은 출처입니다.';document.getElementById('viewer').hidden=false;if(quote&&text){setTimeout(()=>{const idx=text.indexOf(quote);if(idx>=0)pre.scrollTop=Math.max(0,(idx/text.length)*pre.scrollHeight-100);},50);}}
function formatMs(ms){if(ms==null)return '?';const s=Math.floor(ms/1000);return `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;}

function recentViewsKey(){return `hub_recent_views:${currentUser?.id||'anon'}`;}
function loadRecentViews(){try{return JSON.parse(localStorage.getItem(recentViewsKey())||'[]');}catch{return [];}}
function recordRecentView({type,id,title,meta}){if(!title)return;try{let rows=loadRecentViews().filter(r=>!(r.type===type&&String(r.id)===String(id)));rows.unshift({type,id,title,meta,viewedAt:Date.now()});localStorage.setItem(recentViewsKey(),JSON.stringify(rows.slice(0,8)));}catch{}renderRecentViews();}
function renderRecentViews(){const root=document.getElementById('recentViews');if(!root)return;const rows=loadRecentViews();root.replaceChildren();if(!rows.length){root.hidden=true;return;}root.hidden=false;root.appendChild(el('span','muted','최근에 열어본 자료'));rows.forEach(r=>{const b=el('button','search-chip',r.title);b.type='button';b.title=r.meta||'';b.onclick=()=>{if(r.type==='version')openVersion(r.id);else openChunk(r.id);};root.appendChild(b);});}
async function openChunk(chunkId){try{const d=await api(`/api/chunks/${chunkId}`);const title=value(d,'original_name'),meta=`${value(d,'paragraph_ref')||'원문 근거'}`;openViewer({title,meta,evidence:value(d,'content'),summary:value(d,'summary')||'요약 없음',text:value(d,'full_text')||'',quote:value(d,'content')});recordRecentView({type:'chunk',id:chunkId,title,meta:'최근 검색에서 확인'});}catch(err){flash(errorMessage(err),false);}}
const SEARCH_PAGE_SIZE=30; // must match MaterialSearchService.MAX_RESULTS
let lastSearchQuery='',searchLoadedCount=0;
async function runSearchPage(q,offset){
 const data=await api(`/api/projects/${currentProject}/materials/search?q=${encodeURIComponent(q)}&offset=${offset}`);
 renderMaterialResults(document.getElementById('searchResults'),data,q,offset>0);
 searchLoadedCount=offset+data.length;
 const loadMoreBtn=document.getElementById('searchLoadMore');
 if(loadMoreBtn)loadMoreBtn.hidden=data.length<SEARCH_PAGE_SIZE;
}
function renderMaterialResults(root,data,query='',append=false){
 if(!append)root.replaceChildren();
 if(!data?.length){if(!append)root.appendChild(el('div','panel muted','관련 자료를 찾지 못했습니다. 외부 자료가 연결돼 있다면 연동 상태도 확인해 주세요.'));return;}
 const isRag=root.id==='ragSources';
 if(!append)root.appendChild(el('div','search-recommendation-note',isRag?'AI가 먼저 관련 있는 최신 자료를 찾고, 답변에 실제로 사용한 원문입니다.':'파일명, 검색어, 질문과 비슷한 내용을 함께 확인해 가장 관련 있는 최신 자료부터 보여줍니다.'));
 data.forEach((x,index)=>{
   const card=el('article','material-card recommended-material'),top=el('div','material-top');
   const rank=Number(x.recommendationRank||index+1);
   top.appendChild(el('span','recommend-rank',`${rank}순위 추천`));
   const badge=el('span',`source-badge source-${String(x.sourceType||'HUB').toLowerCase().replaceAll('_','-')}`,x.sourceLabel||sourceTypeLabel(x.sourceType));
   top.appendChild(badge);
   if(x.matchType)top.appendChild(el('span','match-type',searchMatchLabel(x.matchType)));
   if(x.itemType)top.appendChild(el('span','muted',x.itemType));
   card.append(top,el('strong','material-title',x.title||'자료'));
   if(x.recommendationReason)card.appendChild(el('div','recommend-reason',x.recommendationReason));
   card.appendChild(el('div','material-location',`위치 · ${x.location||'위치 정보 없음'}`));
   if(x.author||x.sourceCreatedAt)card.appendChild(el('div','muted',[x.author,x.sourceCreatedAt].filter(Boolean).join(' · ')));
   if(x.snippet)card.appendChild(el('p','material-snippet',x.snippet));
   const actions=el('div','material-actions');
   if(isSafeExternalUrl(x.sourceUrl)){
     const a=document.createElement('a');a.className='open-source';a.href=x.sourceUrl;a.target='_blank';a.rel='noopener noreferrer';a.textContent='원문 열기 ↗';actions.appendChild(a);
   }else if(x.sourceType==='HUB'){
     const b=el('button','ghost','Hub 원문 보기');b.onclick=()=>openChunk(x.evidenceId);actions.appendChild(b);
   }else{
     actions.appendChild(el('span','muted','원문 링크 없음'));
   }
   const context=el('button','ghost','관련 업무 함께 보기');context.onclick=()=>{document.getElementById('contextQuery').value=query||x.title;switchView('context');loadContext(query||x.title);};actions.appendChild(context);
   card.appendChild(actions);root.appendChild(card);
 });
}
function isSafeExternalUrl(url){try{return new URL(url).protocol==='https:';}catch{return false;}}

async function loadContext(query){if(!query)return;try{const d=await api(`/api/projects/${currentProject}/context?q=${encodeURIComponent(query)}`);const summary=document.getElementById('contextSummary');summary.replaceChildren();const box=el('div','context-hero');box.appendChild(el('span','eyebrow','관련 업무'));box.appendChild(el('h2','',d.query));box.appendChild(el('p','',d.summary||'관련 자료에서 요약할 근거를 찾지 못했습니다.'));summary.appendChild(box);renderSimple(document.getElementById('contextTodos'),d.todos,x=>`${x.title} · ${reviewStatusLabel(x.reviewStatus)} · ${taskStatusLabel(x.taskStatus)}`);renderSimple(document.getElementById('contextDecisions'),d.decisions,x=>`${value(x,'statement')} · ${reviewStatusLabel(value(x,'review_status'))}`);renderSimple(document.getElementById('contextChanges'),d.changes,x=>`${changeCategoryLabel(value(x,'category'))}: ${value(x,'before_text')||''} → ${value(x,'after_text')||''}`);renderSimple(document.getElementById('contextTimeline'),d.timeline,x=>`${eventTypeLabel(x.eventType)} · ${x.title}`);renderMaterialResults(document.getElementById('contextSources'),d.sources||[],query);}catch(err){flash(err.message,false);}}
function renderSimple(root,rows,label){root.replaceChildren();if(!rows?.length){root.appendChild(el('div','empty','연결된 기록 없음'));return;}rows.forEach(x=>root.appendChild(el('div','item compact',label(x))));}

async function loadConnectorStatus(){
 if(!currentProject)return;const roots=[document.getElementById('connectorStatus'),document.getElementById('connectorStatusDetail')].filter(Boolean);roots.forEach(r=>r.replaceChildren());
 try{const all=await api(`/api/projects/${currentProject}/connectors/status`);
  // One line per service: the panel listed every past sync, so the same connector appeared
  // several times and the newest result was hard to find.
  const seen=new Set(),rows=all.filter(r=>{const k=value(r,'connectorType');if(seen.has(k))return false;seen.add(k);return true;});
  if(!rows.length){roots.forEach(r=>r.appendChild(el('span','muted','아직 외부 서비스에서 가져온 자료가 없습니다.')));return;}
  rows.filter(x=>x.lastStatus==='SUCCESS').forEach(x=>{const label=`${connectorName(x.connectorType)} · 가져오기 완료 · ${x.lastImportedCount||0}건${x.lastSyncedAt?` · 마지막 성공 ${new Date(x.lastSyncedAt).toLocaleString('ko-KR')}`:''}`;roots.forEach(r=>{const b=el('span','sync-state',label);r.appendChild(b);});});if(!rows.some(x=>x.lastStatus==='SUCCESS'))roots.forEach(r=>r.appendChild(el('span','muted','완료된 외부 자료 가져오기가 없습니다.')));}catch{}
}

async function loadRevisions(){const root=document.getElementById('revisionList');root.replaceChildren();if(!currentProject)return;try{const rows=await api(`/api/projects/${currentProject}/revisions`);if(!rows.length){root.appendChild(el('div','panel muted','아직 수정 이력이 없습니다.'));return;}rows.forEach(r=>{const i=el('div','item');i.appendChild(el('strong','',`${entityTypeLabel(value(r,'entity_type'))} · ${eventTypeLabel(value(r,'action'))}`));i.appendChild(el('div','muted',`${value(r,'actor_name')} · ${value(r,'created_at')}`));if(value(r,'before_json')||value(r,'after_json')){const details=document.createElement('details');details.className='technical-details';const summary=document.createElement('summary');summary.textContent='자세한 변경 내용 보기';details.appendChild(summary);if(value(r,'before_json'))details.appendChild(el('pre','history-json',`변경 전\n${value(r,'before_json')}`));if(value(r,'after_json'))details.appendChild(el('pre','history-json',`변경 후\n${value(r,'after_json')}`));i.appendChild(details);}root.appendChild(i);});}catch{root.textContent='이 내용은 관리자만 볼 수 있습니다.';}}
async function loadAdmin(){
 if(currentUser?.globalRole!=='ADMIN')return;
 const [users,applications,audits,reassignments]=await Promise.all([
  api('/api/admin/users'),api('/api/admin/signup-applications'),api('/api/admin/audit'),
  currentProject?api(`/api/admin/projects/${currentProject}/reassignments`):Promise.resolve([])
 ]);
 const pendingCount=document.getElementById('adminPendingCount'),reassignmentSummary=document.getElementById('adminReassignmentSummary'),userCount=document.getElementById('adminUserCount');
 if(pendingCount)pendingCount.textContent=`${applications.length}건`;if(reassignmentSummary)reassignmentSummary.textContent=`${reassignments.length}건`;if(userCount)userCount.textContent=`${users.length}명`;
 const dashPending=document.getElementById('dashPendingCount'),dashReassign=document.getElementById('dashReassignmentCount'),dashUsers=document.getElementById('dashUserCount');
 if(dashPending)dashPending.textContent=`${applications.length}건`;if(dashReassign)dashReassign.textContent=`${reassignments.length}건`;if(dashUsers)dashUsers.textContent=`${users.length}명`;
 const pendingRoot=document.getElementById('signupApplications');pendingRoot.replaceChildren();
 if(!applications.length)pendingRoot.appendChild(el('div','muted','확인할 가입 요청이 없습니다.'));
 applications.forEach(a=>{
  const item=el('div','item signup-application');item.appendChild(el('strong','',`${a.displayName} · 아이디 ${a.loginId}`));
  const legacyOrg=[a.companyName,a.departmentName,a.teamName].filter(Boolean).join(' · ');
  item.appendChild(el('div','muted',`${a.requestedRole==='ADMIN'?'관리자 가입 요청':'일반 사용자 가입 요청'} · 희망 프로젝트: ${a.requestedProjectName||'미지정'}${a.jobTitle?` · ${a.jobTitle}`:''}${legacyOrg?` · ${legacyOrg}`:''}`));
  item.appendChild(el('div','muted',`${a.email} · 신청 ${new Date(a.createdAt).toLocaleString('ko-KR')}`));if(a.signupNote)item.appendChild(el('p','',a.signupNote));
  const actions=el('div','admin-actions');
  const approve=el('button','','가입 허용');approve.onclick=async()=>{try{await api(`/api/admin/signup-applications/${a.id}/approve`,{method:'POST',body:JSON.stringify({})});flash(`가입을 허용했습니다.`);await loadAdmin();}catch(err){flash(errorMessage(err),false);}};
  const approveProject=el('button','ghost','가입 허용하고 현재 프로젝트에 넣기');approveProject.disabled=!currentProject||a.requestedRole==='ADMIN';approveProject.onclick=async()=>{try{await api(`/api/admin/signup-applications/${a.id}/approve`,{method:'POST',body:JSON.stringify({projectId:currentProject})});flash('가입을 허용하고 현재 프로젝트에 추가했습니다.');await loadMembers();await loadAdmin();}catch(err){flash(errorMessage(err),false);}};
  const reject=el('button','ghost','허용하지 않기');reject.onclick=()=>{if(item.querySelector('.signup-reject-inline'))return;const row=el('div','row signup-reject-inline'),input=document.createElement('input'),save=el('button','','가입 허용하지 않기'),cancel=el('button','ghost','취소');input.placeholder='허용하지 않는 이유 (선택)';input.maxLength=1000;save.onclick=async()=>{try{await api(`/api/admin/signup-applications/${a.id}/reject`,{method:'POST',body:JSON.stringify({reason:input.value||null})});flash('이 가입 요청은 허용하지 않았습니다.');await loadAdmin();}catch(err){flash(errorMessage(err),false);}};cancel.onclick=()=>row.remove();row.append(input,save,cancel);item.appendChild(row);input.focus();};
  actions.append(approve,approveProject,reject);item.appendChild(actions);pendingRoot.appendChild(item);
 });
 const userRoot=document.getElementById('adminUsers'),select=document.getElementById('adminUserSelect');userRoot.replaceChildren();select.replaceChildren();
 users.forEach(u=>{
  const i=el('div','item');i.appendChild(el('strong','',`${u.displayName} · 아이디 ${u.loginId}`));
  const status=el('span',`account-status ${u.accountStatus}`,accountStatusLabel(u.accountStatus));i.appendChild(el('div','muted',`${u.companyName||'회사 미입력'}${u.departmentName?` · ${u.departmentName}`:''}${u.teamName?` · ${u.teamName}`:''} · ${u.email} · ${u.jobTitle||'직급 미입력'} · ${u.globalRole==='ADMIN'?'관리자':'팀원'} · `));i.lastChild.appendChild(status);
  const actions=el('div','admin-actions');
  if(u.accountStatus!=='WITHDRAWN'){
   const statusBtn=el('button','ghost',u.accountStatus==='ACTIVE'?'사용 잠시 멈추기':'다시 사용하게 하기');statusBtn.onclick=async()=>{const next=u.accountStatus==='ACTIVE'?'SUSPENDED':'ACTIVE';if(next==='SUSPENDED'&&!confirm(`${u.displayName}님의 Hub 사용을 잠시 멈출까요?\n이 사용자는 바로 로그인할 수 없게 되고, 미완료 업무는 담당자 확인이 필요할 수 있습니다.`))return;const reason=next==='SUSPENDED'?(await requestText('사용을 잠시 멈추는 이유','이유 (선택)')||''):null;try{const d=await api(`/api/admin/users/${u.id}/status`,{method:'PATCH',body:JSON.stringify({status:next,reason})});flash(`사용 상태를 바꿨습니다${d.reassignmentCount?` · 담당자를 다시 정할 일 ${d.reassignmentCount}건`:''}`);if(next==='ACTIVE')flash('사용을 다시 시작했습니다. 정지할 때 프로젝트 배정이 해제되었으니 필요한 프로젝트에 다시 추가해 주세요.',false);await loadMembers();await loadAdmin();}catch(err){flash(errorMessage(err),false);}};actions.appendChild(statusBtn);
   const role=el('button','ghost',u.globalRole==='ADMIN'?'관리 권한 빼기':'관리 권한 주기');role.onclick=async()=>{const makingAdmin=u.globalRole!=='ADMIN';if(!confirm(makingAdmin?`${u.displayName}님에게 회사 관리 권한을 줄까요?\n가입 승인, 사용자 관리, 업무 배정 기능을 사용할 수 있게 됩니다.`:`${u.displayName}님의 회사 관리 권한을 뺄까요?\n일반 사용자 기능은 계속 사용할 수 있습니다.`))return;try{await api(`/api/admin/users/${u.id}/role`,{method:'PATCH',body:JSON.stringify({role:u.globalRole==='ADMIN'?'MEMBER':'ADMIN'})});await loadAdmin();}catch(err){flash(errorMessage(err),false);}};actions.appendChild(role);
   const reset=el('button','ghost','임시 비밀번호 설정');reset.onclick=()=>{if(i.querySelector('.reset-password-inline'))return;const row=el('div','row reset-password-inline'),input=document.createElement('input'),save=el('button','','저장'),cancel=el('button','ghost','취소');input.type='password';input.placeholder='임시 비밀번호 (12자 이상, 영문+숫자)';save.onclick=async()=>{if(!input.value)return;try{await api(`/api/admin/users/${u.id}/reset-password`,{method:'POST',body:JSON.stringify({temporaryPassword:input.value})});flash('임시 비밀번호를 만들었습니다. 기존 로그인은 종료됩니다.');await loadAdmin();}catch(err){flash(errorMessage(err),false);}};cancel.onclick=()=>row.remove();row.append(input,save,cancel);i.appendChild(row);input.focus();};actions.appendChild(reset);
  }
  i.appendChild(actions);userRoot.appendChild(i);
  if(u.globalRole==='MEMBER'&&u.accountStatus==='ACTIVE'){const o=document.createElement('option');o.value=u.id;o.textContent=`${u.displayName} (@${u.loginId})`;select.appendChild(o);}
 });
 const memberRoot=document.getElementById('adminProjectMembers');if(memberRoot){memberRoot.replaceChildren();const members=currentProject?await api(`/api/projects/${currentProject}/members`):[];if(!members.length)memberRoot.appendChild(el('div','muted','이 프로젝트에 추가된 사람이 없습니다.'));members.forEach(m=>{const row=el('div','row between member-admin-row'),label=el('span','',`${value(m,'display_name')} · ${value(m,'email')}`),actions=el('div','row'),move=el('button','ghost','다른 프로젝트로 옮기기'),remove=el('button','ghost','이 프로젝트에서 빼기');move.type=remove.type='button';move.onclick=async()=>{const choices=projects.filter(p=>Number(p.id)!==Number(currentProject));if(!choices.length){flash('이동할 다른 프로젝트가 없습니다.',false);return;}const raw=await requestChoice('이동할 프로젝트 선택',choices.map(p=>({value:p.id,label:p.name})));if(!raw)return;const target=choices.find(p=>Number(p.id)===Number(raw));if(!target){flash('올바른 프로젝트를 선택해 주세요.',false);return;}try{const d=await api(`/api/admin/projects/${currentProject}/members/move`,{method:'POST',body:JSON.stringify({userId:Number(value(m,'user_id')),toProjectId:Number(target.id)})});flash(`프로젝트를 옮겼습니다. 담당자를 다시 정할 일 ${d.reassignmentCount||0}건`);await loadMembers();await loadAdmin();}catch(err){flash(errorMessage(err),false);}};remove.onclick=async()=>{try{const d=await api(`/api/admin/projects/${currentProject}/members/${value(m,'user_id')}`,{method:'DELETE'});flash(`프로젝트에서 제외했습니다. 담당자를 다시 정할 일 ${d.reassignmentCount||0}건`);await loadMembers();await loadAdmin();}catch(err){flash(errorMessage(err),false);}};
  const canConfirm=Boolean(value(m,'can_confirm_todos'));const confirmToggle=el('button','ghost',canConfirm?'의사결정권자 권한 빼기':'의사결정권자 권한 주기');confirmToggle.type='button';confirmToggle.onclick=async()=>{try{await api(`/api/admin/projects/${currentProject}/confirm-permission`,{method:'PUT',body:JSON.stringify({userId:Number(value(m,'user_id')),granted:!canConfirm})});flash(canConfirm?'의사결정권자 권한을 뺐습니다.':'의사결정권자 권한을 줬습니다.');await loadMembers();await loadAdmin();}catch(err){flash(errorMessage(err),false);}};
  actions.append(confirmToggle,move,remove);row.append(label,actions);memberRoot.appendChild(row);});}
 const rr=document.getElementById('adminReassignments'),count=document.getElementById('reassignmentCount');
 if(rr){
  rr.replaceChildren();if(count)count.textContent=`${reassignments.length}건`;
  if(!reassignments.length){rr.appendChild(el('div','muted','담당자를 다시 정해야 하는 일이 없습니다.'));}
  else{
   const selectedReassignments=new Set();
   const bulkBar=el('div','panel review-bulk-bar');
   const bulkAssignee=document.createElement('select'),bulkNone=document.createElement('option');bulkNone.value='';bulkNone.textContent='새 담당자 선택';bulkAssignee.appendChild(bulkNone);
   projectMembers.forEach(m=>{const o=document.createElement('option');o.value=value(m,'user_id');o.textContent=value(m,'display_name');bulkAssignee.appendChild(o);});
   const bulkCount=el('span','muted','0건 선택됨');
   const bulkBtn=el('button','','선택한 일을 한 사람에게 배정');bulkBtn.type='button';
   bulkBtn.onclick=async()=>{
    if(!selectedReassignments.size){flash('배정할 항목을 먼저 선택해 주세요.',false);return;}
    if(!bulkAssignee.value){flash('새 담당자를 먼저 선택해 주세요.',false);return;}
    try{
     const d=await api(`/api/admin/projects/${currentProject}/reassignments/bulk-resolve`,{method:'POST',body:JSON.stringify({requestIds:[...selectedReassignments],newAssigneeId:Number(bulkAssignee.value)})});
     const failed=Object.values(d.results||{}).filter(v=>String(v).startsWith('FAILED')).length;
     flash(failed?`일괄 배정 완료 · 실패 ${failed}건은 개별 확인이 필요합니다.`:'선택한 일을 모두 새 담당자에게 배정했습니다.',!failed);
     await refreshAll();
    }catch(err){flash(errorMessage(err),false);}
   };
   bulkBar.append(el('strong','','일괄 배정'),bulkAssignee,bulkBtn,bulkCount);
   rr.appendChild(bulkBar);
   reassignments.forEach(r=>{
    const item=el('div','item reassignment-item');
    const top=el('div','row review-item-top');
    const check=document.createElement('input');check.type='checkbox';check.setAttribute('aria-label','일괄 배정용 선택');
    check.onchange=()=>{if(check.checked)selectedReassignments.add(Number(value(r,'id')));else selectedReassignments.delete(Number(value(r,'id')));bulkCount.textContent=`${selectedReassignments.size}건 선택됨`;};
    top.appendChild(check);item.appendChild(top);
    item.appendChild(el('strong','',`${value(r,'title')} · ${reassignmentReasonLabel(value(r,'reason'))}`));
    item.appendChild(el('div','muted',`이전 담당 ${value(r,'former_assignee_name')||'없음'} · 상태 ${taskStatusLabel(value(r,'task_status'))} · 기한 ${value(r,'due_date')||'미정'}`));
    const row=el('div','row'),assignee=document.createElement('select');const placeholder=document.createElement('option');placeholder.value='';placeholder.textContent='새 담당자 고르기';assignee.appendChild(placeholder);
    projectMembers.forEach(m=>{const o=document.createElement('option');o.value=value(m,'user_id');o.textContent=value(m,'display_name');assignee.appendChild(o);});
    const resolve=el('button','','새 담당자로 정하기');resolve.onclick=async()=>{if(!assignee.value){flash('새 담당자를 선택해 주세요.',false);return;}try{await api(`/api/admin/reassignments/${value(r,'id')}/resolve`,{method:'POST',body:JSON.stringify({newAssigneeId:Number(assignee.value)})});flash('새 담당자를 정했습니다.');await refreshAll();}catch(err){flash(errorMessage(err),false);}};
    row.append(assignee,resolve);item.appendChild(row);rr.appendChild(item);
   });
  }
 }
 const auditRoot=document.getElementById('auditList');auditRoot.replaceChildren();if(!audits.length)auditRoot.appendChild(el('div','panel muted','아직 바뀐 내용 기록이 없습니다.'));audits.forEach(a=>{const i=el('div','item compact');i.appendChild(el('strong','',`${eventTypeLabel(value(a,'action'))} · ${entityTypeLabel(value(a,'target_type'))}`));i.appendChild(el('div','muted',`${value(a,'display_name')||'시스템 자동 처리'} · ${value(a,'project_name')||'전체 조직'} · ${value(a,'created_at')}`));auditRoot.appendChild(i);});
 await Promise.all([loadSearchRules(),loadRevisions(),loadSensitiveTerms(),loadConnectorPolicy()]);
}

async function loadConnectorPolicy(){
 if(currentUser?.globalRole!=='ADMIN')return;
 const root=document.getElementById('connectorPolicyList');if(!root)return;
 try{
  const policy=await api('/api/connector-policy');
  root.replaceChildren();
  Object.keys(policy).forEach(type=>{
   const enabled=Boolean(policy[type]);
   const row=el('div','row between');
   row.appendChild(el('span','',CONNECTOR_LABELS[type]||type));
   const toggle=el('button','ghost',enabled?'끄기':'켜기');toggle.type='button';
   toggle.onclick=async()=>{try{await api(`/api/connector-policy/${type}`,{method:'PUT',body:JSON.stringify({enabled:!enabled})});await loadConnectorPolicy();applyConnectorPolicy();}catch(err){flash(errorMessage(err),false);}};
   row.appendChild(toggle);root.appendChild(row);
  });
 }catch(err){root.replaceChildren(el('div','muted',errorMessage(err)));}
}

async function loadSensitiveTerms(){
 if(currentUser?.globalRole!=='ADMIN')return;
 const root=document.getElementById('sensitiveTermList');if(!root)return;
 try{
  const rows=await api('/api/admin/sensitive-terms');
  root.replaceChildren();
  if(!rows.length){root.appendChild(el('div','muted','등록한 단어나 값이 없습니다.'));return;}
  rows.forEach(t=>{
   const row=el('div','row between');
   row.appendChild(el('span','',t.term));
   const remove=el('button','ghost','삭제');remove.type='button';
   remove.onclick=async()=>{if(!confirm(`'${t.term}'을(를) 해시 대상에서 뺄까요?\n이미 저장된 회의록에는 영향이 없습니다.`))return;try{await api(`/api/admin/sensitive-terms/${t.id}`,{method:'DELETE'});await loadSensitiveTerms();}catch(err){flash(errorMessage(err),false);}};
   row.appendChild(remove);root.appendChild(row);
  });
 }catch(err){root.replaceChildren();root.appendChild(el('div','muted','불러오지 못했습니다.'));}
}

function splitRuleValues(value){return String(value||'').split(/[,\n]/).map(v=>v.trim()).filter(Boolean);}
function resetSearchRuleForm(){const form=document.getElementById('searchRuleForm');if(!form)return;form.reset();form.elements.ruleId.value='';form.elements.priority.value='100';form.elements.mode.value='SMART';form.elements.active.checked=true;}
function editSearchRule(rule){const form=document.getElementById('searchRuleForm');if(!form)return;form.elements.ruleId.value=rule.id||'';form.elements.name.value=rule.name||'';form.elements.aliases.value=(rule.aliases||[]).join(', ');form.elements.patterns.value=(rule.patterns||[]).join(', ');form.elements.targetFile.value=rule.targetFile||'';form.elements.mode.value=String(rule.mode||'SMART').toUpperCase();form.elements.priority.value=String(Number(rule.priority||100)>=500?500:Number(rule.priority||100)>=300?300:100);form.elements.active.checked=Boolean(rule.active);form.scrollIntoView({behavior:'smooth',block:'center'});}
async function loadSearchRules(){
 if(currentUser?.globalRole!=='ADMIN'||!currentProject)return;
 const root=document.getElementById('searchRuleList'),statusNode=document.getElementById('embeddingStatus'),fileList=document.getElementById('searchRuleDocumentList');if(!root)return;
 if(fileList){fileList.replaceChildren();documentsCache.filter(d=>!value(d,'archived')&&!value(d,'source_deleted')).forEach(d=>{const o=document.createElement('option');o.value=value(d,'original_name')||'';fileList.appendChild(o);});}
 try{
  const [rules,status]=await Promise.all([api(`/api/admin/projects/${currentProject}/search/rules`),api(`/api/admin/projects/${currentProject}/search/embedding-status`)]);
  root.replaceChildren();
  if(!rules.length)root.appendChild(el('div','muted','기준 자료가 아직 없습니다. 없어도 기본 검색은 사용할 수 있습니다.'));
  rules.forEach(rule=>{
   const item=el('div','item search-rule-item'),top=el('div','row between'),title=el('strong','',rule.name||'기준 자료'),state=el('span',`status-pill ${rule.active?'ready':'missing'}`,rule.active?'사용 중':'사용 안 함');top.append(title,state);item.appendChild(top);
   if(rule.targetFile)item.appendChild(el('div','muted',`기준 원본 · ${rule.targetFile}`));
   item.appendChild(el('div','muted',`사람들이 부르는 말 · ${(rule.aliases||[]).join(', ')||'자동으로 정함'}`));
   if(rule.patterns?.length)item.appendChild(el('div','muted',`추가 파일 이름 조건 · ${rule.patterns.join(', ')}`));
   item.appendChild(el('div','muted','파일 이름이 달라도 원본 양식의 항목과 내용이 비슷하면 같은 종류의 자료로 찾습니다.'));
   const actions=el('div','admin-actions'),edit=el('button','ghost','수정'),del=el('button','ghost','삭제');edit.type=del.type='button';edit.onclick=()=>editSearchRule(rule);del.onclick=async()=>{try{await api(`/api/admin/projects/${currentProject}/search/rules/${rule.id}`,{method:'DELETE'});flash('기준 자료를 삭제했습니다.');await loadSearchRules();}catch(err){flash(errorMessage(err),false);}};actions.append(edit,del);item.appendChild(actions);root.appendChild(item);
  });
  if(statusNode){const ready=Number(value(status,'ready_versions')||0),failed=Number(value(status,'failed_versions')||0),pending=Number(value(status,'pending_versions')||0);statusNode.textContent=`검색 준비 완료 ${ready} · 다시 확인할 자료 ${failed} · 준비 중 ${pending}`;statusNode.className=`status-pill ${failed||pending?'review':'ready'}`;}
 }catch(err){root.replaceChildren(el('div','notice',errorMessage(err)));if(statusNode)statusNode.textContent='검색 준비 상태를 확인하지 못했습니다';}
}

function renderSearchRuleTest(data){
 const root=document.getElementById('searchRuleTestResult');if(!root)return;root.replaceChildren();const matched=data?.matchedRule;
 if(matched){root.appendChild(el('strong','',`기준 자료 사용 · ${matched.name}`));root.appendChild(el('div','muted',matched.targetFile?`원본 '${matched.targetFile}'의 내용과 구조를 참고해 파일 이름이 다른 자료도 함께 찾습니다.`:`“${matched.matchedAlias}”와 연결된 자료를 먼저 확인합니다.`));}
 else root.appendChild(el('strong','','정해 둔 기준 자료가 없어 질문과 내용이 비슷한 최신 자료를 찾아봅니다.'));
 (data?.results||[]).slice(0,3).forEach((x,i)=>root.appendChild(el('div','item compact',`${i+1}. ${x.title||'자료'} · ${searchMatchLabel(x.matchType)}`)));
}


function renderSummary(){const root=document.getElementById('summaryCards');root.replaceChildren();const today=localDate(),cards=[['바로 실행',todos.filter(x=>todoReadiness(x).cls==='ready'&&x.taskStatus!=='DONE').length],['AI 검토 필요',todos.filter(x=>x.reviewStatus!=='CONFIRMED').length],['오늘 마감',todos.filter(x=>x.dueDate===today&&x.taskStatus!=='DONE').length],['막힘',todos.filter(x=>x.taskStatus==='BLOCKED').length]];cards.forEach(([k,v])=>{const c=el('div','card');c.append(el('span','muted',k),el('strong','',String(v)));root.appendChild(c);});}

// Global Ctrl+K (Cmd+K on Mac) search overlay - reachable from any view, same /materials/search API
// and result rendering as the dashboard's own search box (renderMaterialResults), so results behave identically.
let globalSearchDebounce=null;
function openGlobalSearch(){
 const overlay=document.getElementById('globalSearchOverlay');if(!overlay)return;
 overlay.hidden=false;
 const input=document.getElementById('globalSearchQuery');input.value='';input.focus();
 document.getElementById('globalSearchResults').replaceChildren();
}
function closeGlobalSearch(){
 const overlay=document.getElementById('globalSearchOverlay');if(!overlay||overlay.hidden)return;
 overlay.hidden=true;clearTimeout(globalSearchDebounce);
}
function bindGlobalSearch(){
 const overlay=document.getElementById('globalSearchOverlay'),form=document.getElementById('globalSearchForm'),
   input=document.getElementById('globalSearchQuery'),results=document.getElementById('globalSearchResults'),
   closeBtn=document.getElementById('globalSearchClose');
 if(!overlay||!form||!input||!results)return;
 document.addEventListener('keydown',e=>{
  if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();overlay.hidden?openGlobalSearch():closeGlobalSearch();return;}
  if(e.key==='Escape'&&!overlay.hidden){closeGlobalSearch();}
 });
 overlay.addEventListener('click',e=>{if(e.target===overlay)closeGlobalSearch();});
 closeBtn.onclick=()=>closeGlobalSearch();
 form.onsubmit=e=>e.preventDefault();
 const runSearch=async()=>{
  const q=input.value.trim();
  if(!q){results.replaceChildren();return;}
  if(!currentProject){results.replaceChildren(el('div','panel muted','검색할 프로젝트가 없습니다.'));return;}
  try{const data=await api(`/api/projects/${currentProject}/materials/search?q=${encodeURIComponent(q)}`);renderMaterialResults(results,data,q);}
  catch(err){results.replaceChildren(el('div','panel muted',errorMessage(err)));}
 };
 input.addEventListener('input',()=>{clearTimeout(globalSearchDebounce);globalSearchDebounce=setTimeout(runSearch,200);});
 results.addEventListener('click',()=>setTimeout(closeGlobalSearch,0));
}
bindGlobalSearch();

init().catch(e=>flash(e.message,false));


function selectedFile(form){const input=form?.querySelector('input[type="file"]');return input?.files?.[0]||null;}
function validateClientFile(file,maxBytes,label){if(!file)throw new Error(`${label}을 선택해 주세요.`);if(file.size>maxBytes)throw new Error(`${label}은 ${Math.round(maxBytes/1024/1024)}MB 이하로 올려 주세요.`);}
async function handleDocumentUpload(e,resultRoot,title){
 e.preventDefault();if(!currentProject)return;const form=e.target,file=selectedFile(form);
 try{
  validateClientFile(file,100*1024*1024,'파일');flash('파일을 가져오고 내용을 정리하고 있습니다.');
  const fd=new FormData(form);
  // Spring rejects an empty string where it expects a LocalDate/Long, so a blank optional field must
  // be dropped rather than sent as "" - FormData always includes it once the input/select exists.
  if(!fd.get('dueDate'))fd.delete('dueDate');
  if(!fd.get('assigneeId'))fd.delete('assigneeId');
  const d=await api(`/api/projects/${currentProject}/documents/upload?sourceDate=${encodeURIComponent(localDate())}`,{method:'POST',body:fd});
  const analysis=await waitForJob(d.jobId,'문서 내용 정리');renderAnalysisResult(resultRoot,analysis,title);form.reset();
  flash(d.todoId?'파일 요약과 할 일 후보 정리, 할 일 등록까지 완료되었습니다.':'파일 요약과 할 일 후보 정리가 완료되었습니다.');await refreshAll();
 }catch(err){flash(errorMessage(err),false);}
}

// editCtx is set only for text this account typed directly (documentId + the title/text just saved);
// a recorded/uploaded meeting's transcript has no free-text form to re-edit, so it stays read-only.
function renderAnalysisResult(root,data,title,editCtx){
 if(!root)return;const normalized=normalizeAnalysis(data);root.replaceChildren();const box=el('div','analysis-result');
 const heading=el('div','row between');heading.appendChild(el('strong','',title));
 if(editCtx?.documentId){const edit=el('button','ghost compact-button','✏️ 내용 수정');edit.type='button';edit.onclick=async()=>{
  const result=await requestManualEdit(editCtx.title,editCtx.text);if(!result)return;
  try{flash('수정한 내용을 다시 정리하고 있습니다.');const d=await api(`/api/projects/${currentProject}/documents/${editCtx.documentId}`,{method:'PUT',body:JSON.stringify(result)});
   const analysis=await waitForJob(d.jobId,'수정 내용 정리');renderAnalysisResult(root,analysis,title,{documentId:editCtx.documentId,title:result.title,text:result.text});
   flash('수정한 내용을 반영했습니다.');await refreshAll();
  }catch(err){flash(errorMessage(err),false);}
 };heading.appendChild(edit);}
 box.appendChild(heading);
 box.appendChild(el('p','',normalized.summary||'정리할 내용이 없습니다.'));
 const tasks=normalized.todos,todoIds=tasks.map(t=>t.id).filter(id=>id!=null),canReviewHere=todoIds.length>0&&canConfirmCurrentProject();
 if(tasks.length){
  box.appendChild(el('strong','',`확인할 할 일 ${tasks.length}건`));
  if(canReviewHere){
   const inline=el('div','inline-todo-review');box.appendChild(inline);
   renderInlineTodoReview(inline,todoIds);
  }else{
   tasks.forEach(t=>box.appendChild(el('div','item compact',`${t.title}${(t.dueDate||t.dueDateSuggestion)?` · ${t.dueDate||t.dueDateSuggestion}`:''}`)));
  }
 }else box.appendChild(el('div','muted','새로 정리된 할 일은 없습니다.'));
 const decisions=normalized.decisions;if(decisions.length){box.appendChild(el('strong','',`확인할 결정 ${decisions.length}건`));decisions.forEach(d=>box.appendChild(el('div','item compact',d.statement||'')));}
 const next=el('div','analysis-next',canReviewHere?'할 일 후보를 위에서 바로 수정하고 담당자를 정할 수 있습니다.':'AI가 정리한 내용은 바로 업무로 확정되지 않습니다. 관리자가 원문을 확인한 뒤 담당자와 기한을 정합니다.');box.appendChild(next);root.appendChild(box);
}
const MAX_DIRECT_RECORDING_SECONDS=25*60;
function chooseMime(){const types=['audio/webm;codecs=opus','audio/webm','audio/mp4'];return types.find(t=>window.MediaRecorder?.isTypeSupported?.(t))||'';}
async function startRecording(){
 if(!window.isSecureContext){flash('브라우저 직접 녹음은 localhost 또는 HTTPS에서 사용할 수 있습니다. 사내 다른 PC에서 접속할 때는 HTTPS를 사용해 주세요. 음성 파일 업로드는 계속 사용할 수 있습니다.',false);return;}
 if(!navigator.mediaDevices?.getUserMedia||!window.MediaRecorder){flash('이 브라우저는 직접 녹음을 지원하지 않습니다. 음성 파일을 선택해 주세요.',false);return;}
 try{
  activeStream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true,autoGainControl:true}});if(!activeStream.getAudioTracks().some(t=>t.readyState==='live'))throw new Error('사용 가능한 마이크 입력이 없습니다.');recordChunks=[];const mime=chooseMime();mediaRecorder=new MediaRecorder(activeStream,mime?{mimeType:mime}:undefined);
  mediaRecorder.ondataavailable=e=>{if(e.data.size)recordChunks.push(e.data);};mediaRecorder.start(1000);recordStartedAt=Date.now();
  document.getElementById('recordBtn').disabled=true;document.getElementById('stopBtn').disabled=false;document.getElementById('recordState').textContent='녹음 중 · 중지하면 음성을 글로 바꾸고 핵심 내용을 정리합니다.';recordTimer=setInterval(updateRecordTime,500);
 }catch(e){activeStream?.getTracks().forEach(t=>t.stop());activeStream=null;const message=e?.name==='NotAllowedError'?'마이크 권한이 차단되었습니다. 브라우저 주소창의 마이크 권한을 허용해 주세요.':e?.name==='NotFoundError'?'연결된 마이크를 찾지 못했습니다. 음성 파일 업로드를 이용해 주세요.':`녹음을 시작하지 못했습니다. ${e?.message||'음성 파일 업로드를 이용해 주세요.'}`;flash(message,false);const state=document.getElementById('recordState');if(state)state.textContent=message;}
}
function updateRecordTime(){const s=Math.floor((Date.now()-recordStartedAt)/1000);const root=document.getElementById('recordTime');if(root)root.textContent=`${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;if(s>=MAX_DIRECT_RECORDING_SECONDS&&mediaRecorder?.state==='recording'){flash('회의 근거 시간표시를 위해 한 번의 녹음은 25분까지 자동 저장합니다. 이어서 새 녹음을 시작할 수 있습니다.');void stopRecording();}}
async function stopRecording(){
 if(!mediaRecorder||mediaRecorder.state!=='recording')return;document.getElementById('stopBtn').disabled=true;const done=new Promise(resolve=>mediaRecorder.addEventListener('stop',resolve,{once:true}));mediaRecorder.stop();await done;clearInterval(recordTimer);activeStream?.getTracks().forEach(t=>t.stop());
 const mime=mediaRecorder.mimeType||'audio/webm',ext=mime.includes('mp4')?'m4a':'webm',blob=new Blob(recordChunks,{type:mime});
 try{await analyzeAudioBlob(blob,`meeting.${ext}`);}finally{document.getElementById('recordBtn').disabled=false;document.getElementById('recordState').textContent='다시 녹음할 수 있습니다.';document.getElementById('recordTime').textContent='00:00';mediaRecorder=null;recordChunks=[];}
}
async function uploadAudioFile(){const input=document.getElementById('audioFileInput'),file=input?.files?.[0];if(!file){flash('분석할 음성 파일을 선택해 주세요.',false);return;}try{validateClientFile(file,100*1024*1024,'음성 파일');await analyzeAudioBlob(file,file.name||'meeting-audio');input.value='';}catch(err){flash(errorMessage(err),false);}}
async function analyzeAudioBlob(blob,fileName){
 const state=document.getElementById('recordState');if(state)state.textContent='음성 파일을 올리고 글로 바꾸는 중...' ;const fd=new FormData();fd.append('file',blob,fileName);const title=document.getElementById('meetingTitle')?.value.trim()||'회의';
 try{const d=await api(`/api/projects/${currentProject}/meetings?title=${encodeURIComponent(title)}&meetingAt=${encodeURIComponent(localDateTimeWithOffset())}`,{method:'POST',body:fd});const analysis=await waitForJob(d.jobId,'회의 음성 AI 분석');renderAnalysisResult(document.getElementById('meetingResult'),analysis,'회의 요약');flash('회의 음성 분석을 완료했습니다.');await refreshAll();}catch(e){flash(errorMessage(e),false);}finally{if(state)state.textContent='녹음 또는 음성 파일 분석을 다시 실행할 수 있습니다.';}
}
