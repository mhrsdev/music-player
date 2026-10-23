#!/usr/bin/env python3
"""Render a local HTML page in headless Chromium and report measurable facts about it.

Standard library only. Needs an installed Chromium-family browser and installs nothing.
Writes only under --out. It measures overflow, load errors, names, contrast and theme
response, lists machine-made signals (habitual patterns worth a second look) and saves
screenshots to look at. It cannot judge beauty, clarity or usability, and a signal is not a defect.
"""
import argparse
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

PROBE = r"""<script>(function(){
var errs=[];
addEventListener('error',function(e){if(e.target&&e.target!==window){errs.push('failed to load '+(e.target.currentSrc||e.target.src||e.target.href||e.target.tagName));}else{errs.push(String(e.message||'script error'));}},true);
addEventListener('unhandledrejection',function(e){errs.push('unhandled rejection: '+String(e.reason));});
function rgb(s){var n=s.match(/[\d.]+/g);return n?[+n[0],+n[1],+n[2],n[3]===undefined?1:+n[3]]:[0,0,0,0];}
function over(f,b){return f.slice(0,3).map(function(v,i){return v*f[3]+b[i]*(1-f[3]);});}
function lum(c){return c.map(function(v){v/=255;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}).reduce(function(s,v,i){return s+v*[.2126,.7152,.0722][i];},0);}
function tag(el){return el.tagName.toLowerCase()+(el.id?'#'+el.id:'')+(typeof el.className==='string'&&el.className.trim()?'.'+el.className.trim().split(/\s+/)[0]:'');}
function run(){
 var de=document.documentElement,cw=de.clientWidth,body=document.body,all=Array.prototype.slice.call(body.querySelectorAll('*'));
 var R={clientWidth:cw,scrollWidth:de.scrollWidth,scrollHeight:de.scrollHeight,dark:matchMedia('(prefers-color-scheme: dark)').matches,
  title:document.title,lang:de.lang,dir:getComputedStyle(de).direction,viewportMeta:!!document.querySelector('meta[name=viewport]'),errors:errs.slice(0,10),
  bodyBackground:getComputedStyle(body).backgroundColor,h1:document.querySelectorAll('h1').length};
 var off=[];
 all.forEach(function(el){if(off.length>=6)return;var r=el.getBoundingClientRect();if(!r.width||!(r.right>cw+1||r.left<-1))return;
  for(var a=el.parentElement;a&&a!==body;a=a.parentElement){var o=getComputedStyle(a).overflowX;if(o==='auto'||o==='scroll'||o==='hidden'||o==='clip')return;}
  off.push(tag(el)+' right='+Math.round(r.right)+' left='+Math.round(r.left));});
 R.overflowing=off;
 R.imagesWithoutAlt=Array.prototype.filter.call(document.images,function(i){return !i.hasAttribute('alt');}).length;
 R.unnamedControls=Array.prototype.filter.call(document.querySelectorAll('button,a[href],input:not([type=hidden]),select,textarea,[role=button]'),function(el){
  var r=el.getBoundingClientRect();if(!r.width||el.closest('[hidden]'))return false;
  return !((el.labels&&el.labels.length)||el.getAttribute('aria-label')||el.getAttribute('aria-labelledby')||el.getAttribute('title')||(el.textContent||'').trim());}).slice(0,6).map(tag);
 R.smallTargets=Array.prototype.filter.call(document.querySelectorAll('button,input:not([type=hidden]),select,[role=button]'),function(el){
  var r=el.getBoundingClientRect(),t=(el.labels&&el.labels[0])?el.labels[0].getBoundingClientRect():r;
  return r.width&&!el.closest('[hidden]')&&(Math.max(r.width,t.width)<24||Math.max(r.height,t.height)<24);}).slice(0,6).map(tag);
 R.invisibleAtRest=all.filter(function(el){var s=getComputedStyle(el),r=el.getBoundingClientRect();if(!r.width||!r.height||el.closest('[hidden]'))return false;
  var own=Array.prototype.filter.call(el.childNodes,function(n){return n.nodeType===3&&n.textContent.trim();}).length;
  return own&&(parseFloat(s.opacity)<.05||s.visibility==='hidden');}).slice(0,6).map(tag);
 R.placeholderText=/lorem ipsum|dolor sit amet/i.test(body.innerText||'');
 var fails=[],checked=0;
 all.forEach(function(el){var s=getComputedStyle(el),r=el.getBoundingClientRect();if(!r.width||!r.height||s.visibility==='hidden'||s.display==='none'||/^(SCRIPT|STYLE|NOSCRIPT)$/.test(el.tagName))return;
  var text=Array.prototype.filter.call(el.childNodes,function(n){return n.nodeType===3;}).map(function(n){return n.textContent.trim();}).join(' ').trim();if(!text)return;
  var chain=[];for(var a=el;a;a=a.parentElement)chain.push(rgb(getComputedStyle(a).backgroundColor));
  var bg=[255,255,255];chain.reverse().forEach(function(c){bg=over(c,bg);});
  var fg=over(rgb(s.color),bg),x=lum(fg),y=lum(bg),ratio=(Math.max(x,y)+.05)/(Math.min(x,y)+.05);
  var large=parseFloat(s.fontSize)>=24||(parseFloat(s.fontSize)>=18.666&&parseFloat(s.fontWeight)>=700),min=large?3:4.5;
  checked++;if(ratio+.001<min&&fails.length<8)fails.push({text:text.slice(0,48),ratio:Math.round(ratio*100)/100,minimum:min,element:tag(el)});});
 R.contrast={checked:checked,failures:fails};
 R.fonts=(document.fonts?Array.prototype.map.call(document.fonts,function(f){return f.family+':'+f.status;}):[]);
 var T={},vh=innerHeight||900;
 function add(key,el){var o=T[key]||(T[key]={count:0,examples:[],first:0});o.count++;if(el){var fq=el.getBoundingClientRect();if(fq.top<vh&&fq.bottom>0)o.first++;if(o.examples.length<3)o.examples.push(tag(el));}}
 function addN(key,n){if(n){var o=T[key]||(T[key]={count:0,examples:[],first:0});o.count+=n;}}
 function hsl(c){var r=c[0]/255,g=c[1]/255,b=c[2]/255,mx=Math.max(r,g,b),mn=Math.min(r,g,b),l=(mx+mn)/2,h=0,sa=0;if(mx!==mn){var d=mx-mn;sa=l>.5?d/(2-mx-mn):d/(mx+mn);h=mx===r?(g-b)/d+(g<b?6:0):mx===g?(b-r)/d+2:(r-g)/d+4;h*=60;}return [h,sa,l];}
 function fam(el){return (getComputedStyle(el).fontFamily||'').split(',')[0].replace(/["']/g,'').trim();}
 var heads=Array.prototype.slice.call(document.querySelectorAll('h1,h2,h3'));
 heads.forEach(function(h){var q=h.previousElementSibling;if(!q||!/^(P|DIV|SPAN|SMALL|LABEL)$/.test(q.tagName))return;
  var t=(q.textContent||'').trim(),w=t?t.split(/\s+/).length:0,st=getComputedStyle(q);
  if(t&&w<=7&&parseFloat(st.fontSize)<=14.5&&(st.textTransform==='uppercase'||parseFloat(st.letterSpacing)>=0.8||/mono|courier|consolas/i.test(st.fontFamily)))add('eyebrow',q);});
 var radii={},boxes=0;
 all.forEach(function(el){var st=getComputedStyle(el),bi=st.backgroundImage||'',q=el.getBoundingClientRect();
  if((st.webkitBackgroundClip==='text'||st.backgroundClip==='text')&&/gradient/.test(bi))add('gradientText',el);
  if(/gradient/.test(bi)&&!/repeating/.test(bi)){var cols=(bi.match(/rgba?\([^)]*\)/g)||[]).map(function(x){return hsl(rgb(x));}).filter(function(x){return x[1]>.3;});
   if(cols.length>=2&&cols.some(function(x){return x[0]>=250&&x[0]<=320;}))add('purpleBlueGradient',el);
   if(/^\d+(\.\d+)?px/.test(st.backgroundSize||'')&&parseFloat(st.backgroundSize)<=80)add('decorativeGrid',el);}
  if((st.backdropFilter&&st.backdropFilter!=='none')||(st.webkitBackdropFilter&&st.webkitBackdropFilter!=='none'))add('glass',el);
  var bl=parseFloat(st.borderLeftWidth),brw=parseFloat(st.borderRightWidth),bt=parseFloat(st.borderTopWidth),rad=parseFloat(st.borderTopLeftRadius)||0;
  if(q.width>80&&rad>=6&&bt<=1&&(bl>=3||brw>=3))add('sideStripeCard',el);
  if(el.children.length===1&&/^(svg|img|i)$/i.test(el.children[0].tagName)&&!(el.textContent||'').trim()&&q.width>=28&&q.width<=80&&Math.abs(q.width-q.height)<=4&&rgb(st.backgroundColor)[3]>0&&rad>0)add('iconTile',el);
  if(el!==body&&q.width>60&&q.width<cw*.9&&q.height>28&&rad>0&&(rgb(st.backgroundColor)[3]>0||bt>0)){var rv=Math.round(rad);radii[rv]=(radii[rv]||0)+1;boxes++;}
  if(el.tagName==='SVG'||el.tagName==='svg'){if(q.width>=240&&q.height>=160)add('largeSvg',el);}
  var kids=Array.prototype.filter.call(el.children,function(k){var kr=k.getBoundingClientRect();return kr.width>100&&kr.height>60;});
  if(kids.length>=3&&el.tagName!=='OL'){var keys={},ps=getComputedStyle(el);
   kids.forEach(function(k){var ks=getComputedStyle(k),kr=k.getBoundingClientRect();
    var surface=(rgb(ks.backgroundColor)[3]>0&&ks.backgroundColor!==ps.backgroundColor)||parseFloat(ks.borderTopWidth)>0||ks.boxShadow!=='none';
    if(!surface||!k.querySelector('h2,h3,h4,strong')||k.querySelector('input,select,textarea'))return;
    var key=Math.round(kr.width/4)+'x'+Math.round(kr.height/12)+'r'+Math.round(parseFloat(ks.borderTopLeftRadius)||0);(keys[key]=keys[key]||[]).push(k);});
   Object.keys(keys).forEach(function(k){if(keys[k].length>=3){var o=T.identicalCards||(T.identicalCards={count:0,examples:[],largest:0});o.count++;o.largest=Math.max(o.largest,keys[k].length);if(o.examples.length<3)o.examples.push(tag(el)+' x'+keys[k].length);}});}
  if(st.animationName&&st.animationName!=='none'){add('animated',el);if(st.animationIterationCount==='infinite')add('infiniteAnimation',el);}
  if(/^(A|BUTTON)$/.test(el.tagName)&&/(?:→|›|»|->)\s*$/.test((el.textContent||'').trim()))add('ctaArrow',el);
  if(/^0[1-9]$/.test((el.textContent||'').trim())&&el.children.length===0)add('zeroPaddedNumber',el);});
 var rtop=Object.keys(radii).sort(function(a,b){return radii[b]-radii[a];})[0];
 R.radius={boxes:boxes,top:rtop===undefined?0:+rtop,share:boxes?radii[rtop]/boxes:0};
 var txt=body.innerText||'';
 function cnt(re){return (txt.match(re)||[]).length;}
 addN('emDash',cnt(/—/g));
 addN('fillerWords',cnt(/\b(?:seamless(?:ly)?|elevat(?:e|es|ed|ing)|unlock(?:s|ed|ing)?|supercharg\w*|empower\w*|streamlin\w*|revolutioniz\w*|game-chang\w*|cutting-edge|next-gen\w*|world-class|enterprise-grade|leverag\w*|robust|effortless(?:ly)?|all-in-one|reimagin\w*)\b/gi));
 addN('contrastSlogan',cnt(/\bnot (?:just|only|merely) [^.!?\n]{1,60}\bbut\b/gi)+cnt(/\bnot an? [^.!?\n]{2,30}\.\s+(?:an?|the) /gi));
 addN('roundStats',cnt(/(?:\b\d{1,3}x\b|\b99\.\d+%|\b\d{1,3},000\+|\b\d+\+ (?:teams|users|customers|companies|researchers)\b)/gi));
 addN('socialProof',cnt(/\b(?:trusted by|loved by|join (?:over )?\d|as seen (?:in|on)|testimonials?)\b/gi)+(txt.match(/★/g)||[]).length);
 addN('stockNames',cnt(/\b(?:John Doe|Jane Doe|Jane Smith|Sarah (?:Chen|Johnson)|Michael (?:Chen|Johnson)|Acme|Globex|Initech|TechCorp)\b/g));
 addN('emoji',(txt.match(/[\u{1F300}-\u{1FAFF}\u{2728}\u{2705}\u{274C}\u{26A1}]/gu)||[]).length);
 var lifts=0;try{Array.prototype.forEach.call(document.styleSheets,function(ss){Array.prototype.forEach.call(ss.cssRules||[],function(r){if(r.selectorText&&r.selectorText.indexOf(':hover')>-1&&/translate[XY]?\(-|scale\(1\.0|scale3d\(1\.0/.test(r.cssText))lifts++;});});}catch(e){}
 R.hoverLifts=lifts;
 var tb=0,tc=0;Array.prototype.forEach.call(document.querySelectorAll('h1,h2,h3,p,li'),function(el){var q=el.getBoundingClientRect(),t=(el.textContent||'').trim();if(!q.width||t.length<20)return;tb++;if(getComputedStyle(el).textAlign==='center')tc++;});
 R.centered={blocks:tb,share:tb?tc/tb:0};
 R.fontsUsed={body:fam(body),heading:heads[0]?fam(heads[0]):''};
 var h1=document.querySelector('h1');
 if(h1){var hr=h1.getBoundingClientRect();
  for(var ai=0;ai<all.length&&!T.pillBadge;ai++){var pe=all[ai];
   if(pe===h1||h1.contains(pe)||pe.contains(h1)||pe.closest('nav,[role=navigation],a,button'))continue;
   var pq=pe.getBoundingClientRect();
   if(!pq.width||pq.bottom>hr.top+10||pq.bottom<hr.top-220||pq.width<40||pq.width>600||pq.height<16||pq.height>56)continue;
   var pt=(pe.textContent||'').trim();
   if(pt.length<3||pt.length>80||pt.split(/\s+/).length>6||pe.children.length>3)continue;
   var ps=getComputedStyle(pe),prad=parseFloat(ps.borderTopLeftRadius)||0;
   if(prad>=Math.min(999,pq.height/2-1)&&(rgb(ps.backgroundColor)[3]>=.05||parseFloat(ps.borderTopWidth)>=1))add('pillBadge',pe);}
  var runs=[];
  (function walk(n){Array.prototype.forEach.call(n.childNodes,function(c){
   if(c.nodeType===3){if(c.textContent.trim().length>=2){var cs=getComputedStyle(c.parentElement);
    runs.push({f:(cs.fontFamily||'').split(',')[0],i:/italic|oblique/.test(cs.fontStyle),c:rgb(cs.color).slice(0,3).map(function(v){return Math.round(v/24);}).join(),g:(cs.webkitBackgroundClip==='text'||cs.backgroundClip==='text')&&/gradient/.test(cs.backgroundImage)});}}
   else if(c.nodeType===1)walk(c);});})(h1);
  var uniq=function(k){var m={};runs.forEach(function(r){m[String(r[k])]=1;});return Object.keys(m).length;};
  if(runs.length>=2&&(uniq('f')>=2||uniq('i')>=2||uniq('c')>=2||uniq('g')>=2))add('accentedHeadline',h1);}
 all.forEach(function(el){
  var st=getComputedStyle(el),sh=st.boxShadow,cn=typeof el.className==='string'?el.className:'';
  if(sh&&sh!=='none'){var gm=sh.match(/(rgba?\([^)]*\))\s+(-?[\d.]+)px\s+(-?[\d.]+)px\s+([\d.]+)px/);
   if(gm&&+gm[4]>=15){var gc=rgb(gm[1]),gh=hsl(gc);if(gc[3]>=.1&&gh[1]>.3&&gh[2]>.2&&gh[2]<.9)add('coloredGlow',el);}}
  if((/^(A|BUTTON)$/.test(el.tagName)||/\b(btn|button|cta)\b/i.test(cn))&&!/outline|ghost/i.test(cn)){
   var bc=rgb(st.backgroundColor),bh=hsl(bc);if(bc[3]>=.5&&bh[1]>.3&&bh[0]>=250&&bh[0]<=310)add('purpleCta',el);}
  var sk=Array.prototype.filter.call(el.children,function(k){var kr=k.getBoundingClientRect();return kr.width>0&&kr.height>0;});
  if(sk.length>=3&&sk.length<=6&&el.getBoundingClientRect().width>=300){var okc=0,ords=0,price=0;
   sk.forEach(function(k){var best='',size=0;
    Array.prototype.forEach.call(k.querySelectorAll('*'),function(l){if(l.children.length)return;var t=(l.textContent||'').trim();if(!t||t.length>12)return;var fs=parseFloat(getComputedStyle(l).fontSize);if(fs>size){size=fs;best=t;}});
    if(size>=22&&best&&best.length<=10&&/\d[\d,.]*\s*(?:k|m|b|%|x|\+)?/i.test(best)){okc++;if(/^0?[1-9]$|^1[0-2]$/.test(best))ords++;if(/^[$\u20ac\u00a3\u00a5\u20b9]/.test(best))price++;}});
   if(okc===sk.length&&ords!==sk.length&&!price)add('statBanner',el);}});
 var faq=Array.prototype.filter.call(document.querySelectorAll('details>summary,button[aria-expanded]'),function(q){return /\?\s*$/.test((q.textContent||'').trim());}).length;
 addN('faqAccordion',faq>=3?faq:0);
 addN('emojiNav',Array.prototype.filter.call(document.querySelectorAll('nav a,aside a,[role=navigation] a'),function(a){return /^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u.test((a.textContent||'').trim());}).length);
 Array.prototype.forEach.call(document.querySelectorAll('a[href]'),function(a){var hf=a.getAttribute('href');
  if(hf===''||hf==='#'||/^javascript:/i.test(hf)){add('deadLink',a);return;}
  if(hf.charAt(0)==='#'){var tid=decodeURIComponent(hf.slice(1));if(!document.getElementById(tid)&&!document.getElementsByName(tid).length)add('deadLink',a);}});
 addN('clickableDiv',document.querySelectorAll('div[onclick],span[onclick],li[onclick]').length);
 addN('genericCta',Array.prototype.filter.call(document.querySelectorAll('a,button'),function(b){return /^(get started|learn more|sign up|try (?:it )?for free|book a demo)(\s*[\u2192\u203a\u00bb])?$/i.test((b.textContent||'').trim());}).length);
 addN('puffery',cnt(/\b(?:testament to|pivotal|tapestry|nestled|boasts?|groundbreaking|renowned|multifaceted|serves as|stands as|in the heart of|delve[sd]?)\b/gi));
 R.words=txt.split(/\s+/).filter(Boolean).length;
 var rtl=getComputedStyle(de).direction==='rtl'||/^(fa|ar|ur|he)/i.test(de.lang||'');
 if(rtl){var phys=0,topRules=[],PH=/(?:margin|padding)-(?:left|right)\s*:|border-(?:left|right)(?:-[a-z]+)?\s*:|(?:^|[;{\s])(?:left|right)\s*:|text-align\s*:\s*(?:left|right)|float\s*:\s*(?:left|right)/;
  try{Array.prototype.forEach.call(document.styleSheets,function(ss){Array.prototype.forEach.call(ss.cssRules||[],function(r){topRules.push(r);});});}catch(e){}
  (function eachRule(rules){rules.forEach(function(r){if(r.type===1){if(PH.test(r.cssText))phys++;}else if(r.cssRules)eachRule(Array.prototype.slice.call(r.cssRules));});})(topRules);
  addN('rtlPhysical',phys);
  var lsp=0;all.forEach(function(el){var ow=Array.prototype.some.call(el.childNodes,function(n){return n.nodeType===3&&/[\u0600-\u06FF]/.test(n.textContent);});if(ow&&parseFloat(getComputedStyle(el).letterSpacing)>0)lsp++;});
  addN('arabicLetterSpacing',lsp);}
 if((txt.match(/[\u0600-\u06FF]/g)||[]).length>50&&!/^(fa|ar|ur|ps|ckb)/i.test(de.lang||''))addN('langMismatch',1);
 R.headingCount=heads.length;R.tells=T;
 de.setAttribute('data-inspect',JSON.stringify(R));
}
function start(){(document.fonts&&document.fonts.ready?document.fonts.ready:Promise.resolve()).then(function(){setTimeout(run,600);});}
if(document.readyState==='complete')start();else addEventListener('load',start);
})();</script>"""

CANDIDATES = ['headless_shell', 'chrome-headless-shell', 'chromium', 'chromium-browser', 'google-chrome', 'chrome']


def find_browser(explicit):
    if explicit:
        return explicit
    if os.environ.get('CHROME_BIN'):
        return os.environ['CHROME_BIN']
    base = os.environ.get('PLAYWRIGHT_BROWSERS_PATH', '/opt/pw-browsers')
    for pattern in ('chromium_headless_shell-*/*/headless_shell', 'chromium-*/*/chrome'):
        found = sorted(Path(base).glob(pattern)) if Path(base).is_dir() else []
        if found:
            return str(found[-1])
    for name in CANDIDATES:
        path = shutil.which(name)
        if path:
            return path
    return None


def instrumented_copy(page, directory):
    source = page.read_text(encoding='utf-8')
    injection = f'<base href="{page.parent.resolve().as_uri()}/">' + PROBE
    for pattern in (r'<head[^>]*>', r'<html[^>]*>', r'<!doctype[^>]*>'):
        match = re.search(pattern, source, re.I)
        if match:
            source = source[:match.end()] + injection + source[match.end():]
            break
    else:
        source = injection + source
    target = Path(directory) / 'instrumented.html'
    target.write_text(source, encoding='utf-8')
    return target


def launch(browser, extra, url, timeout=90):
    command = [browser, '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1'] + extra + [url]
    return subprocess.run(command, capture_output=True, text=True, timeout=timeout)


def probe(browser, copy, width, height, dark_value):
    done = launch(browser, [f'--blink-settings=preferredColorScheme={dark_value}', f'--window-size={width},{height}',
                            '--virtual-time-budget=6000', '--dump-dom'], copy.as_uri())
    found = re.search(r'data-inspect="([^"]*)"', done.stdout)
    return json.loads(html.unescape(found.group(1))) if found else None


def flags(report):
    notes = []
    if report['scrollWidth'] > report['clientWidth']:
        notes.append(f"page scrolls sideways ({report['scrollWidth']} wide in a {report['clientWidth']} px window)")
    if report['overflowing']:
        notes.append('elements past the window edge: ' + '; '.join(report['overflowing'][:3]))
    if report['errors']:
        notes.append('load or script errors: ' + '; '.join(report['errors'][:3]))
    if report['contrast']['failures']:
        first = report['contrast']['failures'][0]
        notes.append(f"{len(report['contrast']['failures'])}+ text runs below contrast minimum, e.g. '{first['text']}' {first['ratio']}:1 (heuristic; verify text that sits on a gradient or image)")
    if report['imagesWithoutAlt']:
        notes.append(f"{report['imagesWithoutAlt']} image(s) without an alt attribute")
    if report['unnamedControls']:
        notes.append('controls without a name: ' + ', '.join(report['unnamedControls']))
    if report['smallTargets']:
        notes.append('controls under 24 px: ' + ', '.join(report['smallTargets']))
    if report['invisibleAtRest']:
        notes.append('text present but invisible at rest: ' + ', '.join(report['invisibleAtRest']))
    if report['placeholderText']:
        notes.append('placeholder text (lorem ipsum) found')
    if report['h1'] != 1:
        notes.append(f"{report['h1']} h1 elements")
    if not report['viewportMeta']:
        notes.append('no viewport meta tag')
    if not report['title'].strip():
        notes.append('empty <title>')
    failed_fonts = [f for f in report['fonts'] if f.endswith(':error')]
    if failed_fonts:
        notes.append('fonts that failed to load: ' + ', '.join(failed_fonts[:4]))
    return notes


STOCK_FONTS = {'inter', 'roboto', 'geist', 'plus jakarta sans', 'space grotesk', 'instrument serif', 'fraunces', 'bricolage grotesque', 'sora', 'young serif', 'syne'}


def tell_signals(report):
    """Turn raw counts into plain sentences. Signals are prompts to look, never defects by themselves."""
    tells = report.get('tells') or {}
    def n(key):
        return tells.get(key, {}).get('count', 0)
    def where(key):
        tell = tells.get(key, {})
        examples = tell.get('examples') or []
        text = f" (e.g. {', '.join(examples[:2])})" if examples else ''
        if tell.get('first'):
            text += f" [{tell['first']} on the first screen]"
        return text
    notes = []
    if n('eyebrow') >= 2:
        notes.append(f"{n('eyebrow')} small uppercase, tracked or monospace labels sit directly above headings ({report.get('headingCount', 0)} headings){where('eyebrow')}")
    if n('gradientText'):
        notes.append(f"gradient-filled text{where('gradientText')}")
    if n('purpleBlueGradient'):
        notes.append(f"purple-to-blue gradient{where('purpleBlueGradient')}")
    if n('glass'):
        notes.append(f"blurred glass surfaces{where('glass')}")
    if n('decorativeGrid'):
        notes.append(f"decorative grid or dot background{where('decorativeGrid')}")
    if n('sideStripeCard'):
        notes.append(f"{n('sideStripeCard')} rounded card(s) with a thick side stripe{where('sideStripeCard')}")
    if n('iconTile') >= 3:
        notes.append(f"{n('iconTile')} icons in tinted squares or circles{where('iconTile')}")
    cards = tells.get('identicalCards')
    if cards:
        notes.append(f"{cards['count']} group(s) of three or more identical cards, largest {cards['largest']}{where('identicalCards')}")
    radius = report.get('radius') or {}
    if radius.get('boxes', 0) >= 8 and radius.get('share', 0) >= 0.8 and radius.get('top', 0) >= 12:
        notes.append(f"{round(radius['share'] * 100)}% of {radius['boxes']} boxes share one {radius['top']} px corner radius")
    fonts = report.get('fontsUsed') or {}
    stock = sorted({name for name in fonts.values() if name.lower() in STOCK_FONTS})
    if stock:
        notes.append('first-choice font is a common default: ' + ', '.join(stock))
    if report.get('hoverLifts', 0) >= 3:
        notes.append(f"{report['hoverLifts']} hover rules lift or scale their element")
    if n('infiniteAnimation'):
        notes.append(f"{n('infiniteAnimation')} element(s) animate forever{where('infiniteAnimation')}")
    if n('animated') >= 8:
        notes.append(f"{n('animated')} animated elements")
    centered = report.get('centered') or {}
    if centered.get('blocks', 0) >= 6 and centered.get('share', 0) >= 0.6:
        notes.append(f"{round(centered['share'] * 100)}% of text blocks are centered")
    if n('ctaArrow') >= 2:
        notes.append(f"{n('ctaArrow')} buttons or links end in an arrow")
    if n('zeroPaddedNumber') >= 3:
        notes.append(f"{n('zeroPaddedNumber')} zero-padded markers such as 01, 02, 03 (check the order is real)")
    if n('pillBadge'):
        notes.append(f"a pill badge sits above the main headline{where('pillBadge')}")
    if n('accentedHeadline'):
        notes.append('one part of the main headline is styled differently (italic, another face, another color or a gradient)')
    if n('statBanner'):
        notes.append(f"a row of headline statistics{where('statBanner')}")
    if n('faqAccordion'):
        notes.append(f"{n('faqAccordion')} collapsible question-and-answer items")
    if n('emojiNav'):
        notes.append(f"{n('emojiNav')} navigation item(s) start with an emoji")
    if n('coloredGlow') >= 2:
        notes.append(f"{n('coloredGlow')} saturated colored shadows or glows{where('coloredGlow')}")
    if n('purpleCta'):
        notes.append(f"filled purple or indigo call to action{where('purpleCta')}")
    if n('deadLink'):
        notes.append(f"{n('deadLink')} link(s) go nowhere (href '#', empty, or a missing anchor){where('deadLink')}")
    if n('clickableDiv'):
        notes.append(f"{n('clickableDiv')} non-interactive element(s) with an onclick handler")
    if n('genericCta') >= 2:
        notes.append(f"{n('genericCta')} generic call-to-action labels such as Get started or Learn more")
    if n('puffery'):
        notes.append(f"{n('puffery')} promotional or significance phrase(s) such as pivotal, tapestry, boasts, serves as")
    if n('rtlPhysical'):
        notes.append(f"{n('rtlPhysical')} stylesheet rule(s) use physical left or right values on a right-to-left page")
    if n('arabicLetterSpacing'):
        notes.append(f"{n('arabicLetterSpacing')} element(s) set letter-spacing on connected script")
    if n('langMismatch'):
        notes.append('Arabic-script text on a page whose lang is not fa, ar or similar')
    if n('fillerWords'):
        notes.append(f"{n('fillerWords')} filler word(s) such as elevate, unlock, seamless, streamline")
    if n('emDash') >= 3:
        rate = n('emDash') / max(report.get('words') or 1, 1) * 1000
        notes.append(f"{n('emDash')} em dashes ({rate:.1f} per 1,000 words; weak alone, rates vary by model)")
    if n('contrastSlogan'):
        notes.append(f"{n('contrastSlogan')} 'not just X but Y' or 'Not an A. A B.' construction(s)")
    if n('roundStats'):
        notes.append(f"{n('roundStats')} round figure(s) such as 10x, 99.9% or 10,000+ (confirm each is sourced)")
    if n('socialProof'):
        notes.append(f"{n('socialProof')} social-proof phrase(s) or stars (confirm each is real)")
    if n('stockNames'):
        notes.append(f"{n('stockNames')} stock name(s) such as John Doe, Sarah Chen or Acme")
    if n('emoji'):
        notes.append(f"{n('emoji')} emoji in the visible text")
    if n('largeSvg'):
        notes.append(f"{n('largeSvg')} large inline drawing(s): view them at full size and check they look finished{where('largeSvg')}")
    return notes


def signal_density(count):
    """Coarse band for how many distinct signals appeared; several together matter more than one."""
    if count == 0:
        return 'none'
    if count <= 2:
        return 'a few'
    if count <= 4:
        return 'several'
    return 'many'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('page', type=Path)
    parser.add_argument('--out', type=Path, default=Path('inspect-out'), help='where screenshots and report.json go')
    parser.add_argument('--widths', default='390,1440', help='comma-separated viewport widths in CSS px')
    parser.add_argument('--browser', help='Chromium-family executable; otherwise CHROME_BIN, Playwright cache or PATH')
    parser.add_argument('--no-screenshots', action='store_true')
    args = parser.parse_args()
    page = args.page.resolve()
    if not page.is_file():
        parser.exit(2, f'not a file: {page}\n')
    browser = find_browser(args.browser)
    if not browser:
        parser.exit(2, 'no Chromium-family browser found; pass --browser or set CHROME_BIN\n')
    args.out.mkdir(parents=True, exist_ok=True)
    widths = [int(w) for w in args.widths.split(',') if w.strip()]
    states, mapping = [], {'dark': 0, 'light': 1}
    with tempfile.TemporaryDirectory() as scratch:
        copy = instrumented_copy(page, scratch)
        for width in widths:
            for scheme in ('light', 'dark'):
                report = probe(browser, copy, width, 900, mapping[scheme])
                if report and report['dark'] != (scheme == 'dark'):
                    mapping = {'dark': 1 - mapping['dark'], 'light': 1 - mapping['light']}
                    report = probe(browser, copy, width, 900, mapping[scheme])
                state = {'width': width, 'scheme': scheme}
                if not report:
                    state['error'] = 'no metrics returned; the page may have failed to load'
                    states.append(state)
                    continue
                state.update(report)
                state['flags'] = flags(report)
                if not args.no_screenshots:
                    height = max(800, min(report['scrollHeight'], 8000))
                    shot = args.out / f'{page.stem}-{width}-{scheme}.png'
                    launch(browser, [f'--blink-settings=preferredColorScheme={mapping[scheme]}', f'--window-size={width},{height}',
                                     '--virtual-time-budget=6000', f'--screenshot={shot}'], page.as_uri())
                    state['screenshot'] = str(shot) if shot.exists() else None
                states.append(state)
    light = next((s for s in states if s['scheme'] == 'light' and 'bodyBackground' in s), None)
    dark = next((s for s in states if s['scheme'] == 'dark' and 'bodyBackground' in s), None)
    notes = []
    if light and dark and light['bodyBackground'] == dark['bodyBackground']:
        notes.append('page background is identical in light and dark schemes: the page does not respond to the color scheme (fine if deliberate)')
    reference = next((s for s in states if s['width'] == max(widths) and s['scheme'] == 'light' and 'tells' in s), None) \
        or next((s for s in states if 'tells' in s), None)
    signals = tell_signals(reference) if reference else []
    density = signal_density(len(signals))
    result = {'page': str(page), 'browser': browser, 'notes': notes, 'signals': signals, 'signalDensity': density, 'states': states,
              'limits': 'Measured facts only. Contrast ignores images, gradients and overlays. Signals are habitual patterns counted by simple rules and can be wrong either way; a page with none is not thereby well made. It does not judge hierarchy, taste, clarity or usability; read the screenshots.'}
    (args.out / 'report.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    for state in states:
        label = f"{state['width']}px {state['scheme']}"
        if 'error' in state:
            print(f"{label}: {state['error']}")
        else:
            print(f"{label}: " + ('; '.join(state['flags']) if state['flags'] else 'no measurable problems found')
                  + (f" -> {state['screenshot']}" if state.get('screenshot') else ''))
    for note in notes:
        print('note: ' + note)
    if signals:
        print(f'machine-made signals worth a look: {len(signals)} distinct, density {density} (prompts, not defects; several together on the first screen matter most):')
        for signal in signals:
            print('  - ' + signal)
    print(f"full report: {args.out / 'report.json'}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
