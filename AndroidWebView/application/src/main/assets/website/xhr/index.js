var $jscomp=$jscomp||{};$jscomp.scope={};$jscomp.ASSUME_ES5=!1;$jscomp.ASSUME_NO_NATIVE_MAP=!1;$jscomp.ASSUME_NO_NATIVE_SET=!1;$jscomp.defineProperty=$jscomp.ASSUME_ES5||"function"==typeof Object.defineProperties?Object.defineProperty:function(a,b,d){a!=Array.prototype&&a!=Object.prototype&&(a[b]=d.value)};$jscomp.getGlobal=function(a){return"undefined"!=typeof window&&window===a?a:"undefined"!=typeof global&&null!=global?global:a};$jscomp.global=$jscomp.getGlobal(this);$jscomp.SYMBOL_PREFIX="jscomp_symbol_";
$jscomp.initSymbol=function(){$jscomp.initSymbol=function(){};$jscomp.global.Symbol||($jscomp.global.Symbol=$jscomp.Symbol)};$jscomp.Symbol=function(){var a=0;return function(b){return $jscomp.SYMBOL_PREFIX+(b||"")+a++}}();
$jscomp.initSymbolIterator=function(){$jscomp.initSymbol();var a=$jscomp.global.Symbol.iterator;a||(a=$jscomp.global.Symbol.iterator=$jscomp.global.Symbol("iterator"));"function"!=typeof Array.prototype[a]&&$jscomp.defineProperty(Array.prototype,a,{configurable:!0,writable:!0,value:function(){return $jscomp.arrayIterator(this)}});$jscomp.initSymbolIterator=function(){}};$jscomp.arrayIterator=function(a){var b=0;return $jscomp.iteratorPrototype(function(){return b<a.length?{done:!1,value:a[b++]}:{done:!0}})};
$jscomp.iteratorPrototype=function(a){$jscomp.initSymbolIterator();a={next:a};a[$jscomp.global.Symbol.iterator]=function(){return this};return a};$jscomp.makeIterator=function(a){$jscomp.initSymbolIterator();$jscomp.initSymbol();$jscomp.initSymbolIterator();var b=a[Symbol.iterator];return b?b.call(a):$jscomp.arrayIterator(a)};function element(a,b){var d=document.createElement(a);if(null!==b){var g=document.createTextNode(b);d.appendChild(g)}return d}
function xhrLoaded(a,b){return function(){var d=JSON.parse(a.responseText);b.appendChild(element("pre",JSON.stringify(d)))}}function xhrError(a,b){return function(){b.appendChild(element("p","Error."))}}
function getter_listener(a,b){return function(d){function g(a){f[a].appendChild(element("span"," sending"));h[a].send()}d.stopPropagation();var h=Array.from(Array(a),function(){return new XMLHttpRequest}),f=Array.from(Array(a));d=$jscomp.makeIterator(h.entries());for(var c=d.next();!c.done;c=d.next()){var e=$jscomp.makeIterator(c.value);c=e.next().value;e=e.next().value;f[c]=element("div",String(c+1));document.body.appendChild(f[c]);e.open("GET","json/target_"+c+"_.json",!0);e.setRequestHeader("Content-Type",
"application/json");e.onload=xhrLoaded(e,f[c]);e.onerror=xhrError(e,f[c])}d=$jscomp.makeIterator(h.keys());for(c=d.next();!c.done;c=d.next())c=c.value,setTimeout(g,(0+c)*b*1E3,c);document.body.appendChild(element("p","getter_listener finished."))}}function click_listener(a){var b=element("p","Click or tap for XHR. Number:15 Timeout:"+String((500).toFixed(0)));b.addEventListener("click",getter_listener(15,.5));document.body.appendChild(b);a.stopPropagation()}
function main(a){var b=document.getElementById(a);null===b?document.body.appendChild(element("p","getElementById("+a+") null.")):b.addEventListener("click",click_listener)};