import fs from "fs";

const jsPath = "dist/assets/index-C-hY4-44.js";
const c = fs.readFileSync(jsPath, "utf8");

const startMarker = 'i=[{id:"line-a"';
const oldSel = 'r=Fe("CAST-02")';

const si = c.indexOf(startMarker);
const ei = c.indexOf(oldSel);
if (si === -1 || ei === -1 || ei <= si) {
  console.error("Markers not found", { si, ei });
  process.exit(1);
}

const replacement = `i=[{id:"line-a",name:"레이아웃 1 · 통합 공정 라인",area:"A-Block",status:"정상",oee:89,uph:502,equipment:5,active:5,alarm:0,left:"5%",top:"12%",width:"88%",height:"24%",equipStatus:{run:100,stop:0,wait:0,stopEnd:100},balance:90,stations:[88,90,87,91,89],upmh:1180,productivity:93,upmhPercent:85,uphPercent:84},{id:"line-b",name:"레이아웃 2 · 통합 공정 라인",area:"B-Block",status:"경고",oee:84,uph:468,equipment:5,active:4,alarm:1,left:"5%",top:"42%",width:"88%",height:"24%",equipStatus:{run:80,stop:10,wait:10,stopEnd:90},balance:82,stations:[80,78,85,83,81],upmh:1090,productivity:87,upmhPercent:78,uphPercent:76},{id:"line-c",name:"레이아웃 3 · 통합 공정 라인",area:"C-Block",status:"경고",oee:83,uph:455,equipment:5,active:4,alarm:2,left:"5%",top:"72%",width:"88%",height:"22%",equipStatus:{run:78,stop:12,wait:10,stopEnd:88},balance:84,stations:[82,86,84,79,83],upmh:1060,productivity:85,upmhPercent:76,uphPercent:74}],o=[{id:"CAST-01",name:"주조기",type:"주조기",lineId:"line-a",status:"정상",left:"10%",top:"18%",main:"금속 소재 성형 · 용탕 668℃ · CT 41.2s"},{id:"CNC-01",name:"가공기 / CNC",type:"가공기",lineId:"line-a",status:"정상",left:"28%",top:"18%",main:"치수 가공 및 절삭 · 스핀들 7200rpm · CT 36.5s"},{id:"WASH-01",name:"세척기",type:"세척기",lineId:"line-a",status:"정상",left:"46%",top:"18%",main:"이물 제거 및 세척 · 세척수 62℃ · 건조 84℃"},{id:"ASSY-01",name:"조립기",type:"조립기",lineId:"line-a",status:"정상",left:"64%",top:"18%",main:"부품 체결 및 조립 · 체결토크 39Nm · CT 34.8s"},{id:"TEST-01",name:"검사기",type:"검사기",lineId:"line-a",status:"경고",left:"82%",top:"18%",main:"최종 품질 검사 · 치수 허용 범위 모니터링"},{id:"CAST-01-B",name:"주조기",type:"주조기",lineId:"line-b",status:"경고",left:"10%",top:"48%",main:"금속 소재 성형 · 용탕 온도 상한 근접"},{id:"CNC-01-B",name:"가공기 / CNC",type:"가공기",lineId:"line-b",status:"정상",left:"28%",top:"48%",main:"치수 가공 및 절삭 · 공구 수명 72%"},{id:"WASH-01-B",name:"세척기",type:"세척기",lineId:"line-b",status:"정상",left:"46%",top:"48%",main:"이물 제거 및 세척 · 농도 보정 완료"},{id:"ASSY-01-B",name:"조립기",type:"조립기",lineId:"line-b",status:"정상",left:"64%",top:"48%",main:"부품 체결 및 조립 · 체결각도 정상"},{id:"TEST-01-B",name:"검사기",type:"검사기",lineId:"line-b",status:"정상",left:"82%",top:"48%",main:"최종 품질 검사 · 샘플 검증 OK"},{id:"CAST-01-C",name:"주조기",type:"주조기",lineId:"line-c",status:"정상",left:"10%",top:"78%",main:"금속 소재 성형 · 압력·금형 온도 안정"},{id:"CNC-01-C",name:"가공기 / CNC",type:"가공기",lineId:"line-c",status:"정상",left:"28%",top:"78%",main:"치수 가공 및 절삭 · 목표 공차 유지"},{id:"WASH-01-C",name:"세척기",type:"세척기",lineId:"line-c",status:"정상",left:"46%",top:"78%",main:"이물 제거 및 세척 · 세척 입구 압력 정상"},{id:"ASSY-01-C",name:"조립기",type:"조립기",lineId:"line-c",status:"이상",left:"64%",top:"78%",main:"부품 체결 및 조립 · 일시 정지 · 하중 편차"},{id:"TEST-01-C",name:"검사기",type:"검사기",lineId:"line-c",status:"정상",left:"82%",top:"78%",main:"최종 품질 검사 · 전수 검사 진행"}]`;

const newContent =
  c.slice(0, si) + replacement + ",r=Fe(\"CAST-01\")" + c.slice(ei + oldSel.length);

fs.writeFileSync(jsPath, newContent);
console.log("Patched", jsPath);
console.log("Length delta", newContent.length - c.length);
