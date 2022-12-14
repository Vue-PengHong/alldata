# ðº Logan Web SDK
å¯å¨ Web å¹³å°ï¼ H5 æ PC ç¯å¢ï¼ä¸è¿è¡ç Logan ç»ä»¶ï¼å®ç°åç«¯æ¥å¿çæ¬å°å­å¨ä¸ä¸æ¥åè½ã

[English Readme](https://github.com/Meituan-Dianping/Logan/tree/master/Logan/WebSDK/README.md)

## åç«¯æ¥å¿çå·¥ä½æµ
å¾å¤æ¶åï¼å¼åèæ¬å°é¾ä»¥å¤ç°æè§¦è¾¾ç¨æ·ç«¯çå¼å¸¸æåµãè¿ç§æ¶åï¼ç«¯ä¸å®æ´çæ¥å¿æµåä¸ä¸æä¿¡æ¯å°å¸®å©å¼åèæ´ææå°è¿åé®é¢ç°åºï¼å®ä½å¹¶è§£å³è¿äºçé¾æçãç¶èå¤§ä½ç§¯æ¥å¿æµçå®æ¶ä¸æ¥å°èè´¹å·¨å¤§çç¨æ·åä¼ä¸æµéï¼çæ­£è½å¸®å©å¼åèè§£å³é®é¢çå´åªææå°é¨åãå æ­¤ Logan å¨å®ç°åç«¯æ¥å¿æµçå­å¨ä¸ä¸æ¥æ¶ï¼éç¨çæ¯ç¨æ·ç«¯æ¥å¿æ¬å°å­å¨ç»åé®é¢åé¦æ¶è§¦åä¸æ¥çæ¹å¼ï¼

<img style="width:70%; max-width:70%;" src="https://raw.githubusercontent.com/Meituan-Dianping/Logan/master/Logan/WebSDK/img/logan_web_workflow.png"/>

## æ¥å¥æ¹å¼
ä¸è½½ npm å

```
npm install --save logan-web
```
æè

```
yarn add logan-web
```

## ç¯å¢éè¦
logan-web ä½¿ç¨äºå¨æå¯¼å¥ (dynamic imports) æ¥åå²ä»£ç ï¼ç®çæ¯å®ç°æéå è½½ãå æ­¤ä½ éè¦ä½¿ç¨ [webpack](https://webpack.docschina.org/) æ¥æåä½ çé¡¹ç®ãå¦æä½ å¯¹ webpack è¿ä¸å¤ªçæçè¯ï¼å¯ä»¥åè [Logan Web SDK Example](https://github.com/Meituan-Dianping/Logan/tree/master/Example/Logan-WebSDK) è¯¥æä»¶å¤¹ã


## ç®åä¸æ
### ð æ¥å¿å­å¨
å¨èæ¬ä»£ç ä¸­ä½ å¯ä»¥ä½¿ç¨ log() æ¹æ³æ¥è®°å½æ¥å¿åå®¹ãæ¥å¿ä¿¡æ¯ä¼è¢« Logan Web æåºä¿å­å¨æ¬å°æµè§å¨ç IndexedDB åºä¸­ãlog æ¹æ³çè°ç¨æ¹å¼æ¯åæ­¥çï¼å¶åé¨ä¼å¼æ­¥æ§è¡æ¥å¿çæ¬å°å­å¨ï¼ä½ æ éç­å¾æ¥å¿çå­å¨ç»æè¿åãå¦æä½ å¾å³å¿å­å¨è¿ç¨æ¯å¦åçå¼å¸¸ï¼å¯ä»¥å¨ initConfig æ¹æ³ä¸­éç½® errorHandler æ¥è·åå­å¨æ¶å¼å¸¸ã

```js
import Logan from 'logan-web';
let logContent = 'Your log content';
let logType = 1;

Logan.log(logContent, logType);
```

### ð¤ æ¥å¿ä¸æ¥
ä½ å¯ä»¥å¨ç¨æ·å¨é¡µé¢ç¹å»åé¦æèä»£ç ææå¼å¸¸ç­æ¶æºï¼è°ç¨å¼æ­¥ report() æ¹æ³æ¥è§¦å Logan æ¬å°æ¥å¿çä¸æ¥ãLogan çæ¬å°æ¥å¿æ¯æç§å¤©å­å¨çï¼å æ­¤ä½ éè¦éè¿åæ°åè¯ Logan ä½ æ³ä¸æ¥åªå å¤©çæ¥å¿åå®¹ã

```js
import Logan from 'logan-web';
const reportResult = await Logan.report({
    reportUrl: 'https://yourServerAddressToAcceptLogs',
    deviceId: 'LocalDeviceIdOrUnionId',
    fromDayString: '2019-11-06',
    toDayString: '2019-11-07'
});

console.log(reportResult);
/* e.g.
{ 
	2019-11-06: {msg: "No log exists"},
	2019-11-07: {msg: "Report succ"}
}
*/
```

## API

### ð initConfig(globalConfig)
è¯¥æ¹æ³ä¸º Logan åä¾è®¾å®å¨å±éç½®ãä¸è¬æåµä¸ä½ åªéå¨å¼å¥ Logan åæ§è¡ä¸æ¬¡è¯¥æ¹æ³ï¼è®¾å®å¥½å¨å±åæ°å³å¯ãè¯¥æ¹æ³æ¯æ¬¡è¢«è°ç¨æ¶ï¼é½ä¼è¦çç°æææç Logan å¨å±éç½®ãè¯¥æ¹æ³ä¸æ¯å¿è¦çï¼ä»¥ä¸éç½®åæ°ä¹é½æ¯å¯éçã

* globalConfig: å¨å±åæ°çéç½®å¯¹è±¡ã
	* reportUrl (å¯é): ç¨äºæ¥æ¶ä¸æ¥æ¥å¿çæå¡å¨å°åãå¦æå¨è°ç¨ report æ¹æ³æ¶ä¹éç½®äº reportUrlï¼ä¼ä¼åéç¨é£ä¸ªå°åè¿è¡ä¸æ¥ã
	
	* publicKey (å¯é): 1024 ä½ç RSA å å¯å¬é¥. å¦æä½ éè¦è°ç¨ logWithEncryption() æ¹æ³å¯¹æ¬å°æ¥å¿è¿è¡å å¯æä½ï¼é£ä¹ä½ å¿é¡»äºåéç½®è¯¥å¬é¥ãä¸è¯¥å¬é¥éå¯¹çç§é¥å­å¨äºä½ çæå¡å¨ä¸ã
	
	* logTryTimes (å¯é): Logan å¨éå°æ¬å°å­å¨å¤±è´¥çæåµä¸ï¼ä¼å°è¯çæ¬¡æ°ãé»è®¤ä¸º 3 æ¬¡ãå¦æ Logan å­å¨å¤±è´¥äº logTryTimes æ¬¡æ°åå°ä¸åè¿è¡åç»­æ¥å¿çå­å¨ã
	
	* dbName (å¯é): ä½ å¯ä»¥éç½®è¯¥é¡¹æ¥èªå®ä¹æ¬å° DB åºçåå­ãé»è®¤ä¸º logan\_web\_dbãä¸åDB åºä¹é´çæ°æ®æ¯éç¦»èä¸åå½±åã
	
	* errorHandler (å¯é): ä½ å¯ä»¥éç½®è¯¥é¡¹æ¥æ¥æ¶ log() å logWithEncryption() æ¹æ³å¯è½äº§ççå¼å¸¸. Logan ç log å logWithEncryption æ¹æ³å¨åºå±ä¼æ§è¡å¼æ­¥å­å¨ï¼å æ­¤ä½ æ éç­å¾è¿ä¸¤ä¸ªæ¹æ³çè¿åãå¦æä½ ç¡®å®æ³ç¥é Logan å¨å­å¨æ¶æ¯å¦æ¥éäºï¼ä½ å¯ä»¥éç½®è¯¥æ¹æ³æ¥è·åå¼å¸¸ã

	* succHandler (å¯é): ä½ å¯ä»¥éç½®è¯¥é¡¹åè°ï¼è¯¥æ¹æ³ä¼å¨ log() å logWithEncryption() æ¹æ³åå¼æ­¥å­å¨æ¥å¿æååæ§è¡ã

```js
import Logan from 'logan-web';
Logan.initConfig({
	reportUrl: 'https://yourServerAddressToAcceptLogs',
	publicKey: '-----BEGIN PUBLIC KEY-----\n'+
        'MIGeMA0GCSqGSIb3DQEBAQUAA4GMADCBiAKBgG2m5VVtZ4mHml3FB9foDRpDW7Pw\n'+
        'Foa+1eYN777rNmIdnmezQqHWIRVcnTRVjrgGt2ndP2cYT7MgmWpvr8IjgN0PZ6ng\n'+
        'MmKYGpapMqkxsnS/6Q8UZO4PQNlnsK2hSPoIDeJcHxDvo6Nelg+mRHEpD6K+1FIq\n'+
        'zvdwVPCcgK7UbZElAgMBAAE=\n'+
        '-----END PUBLIC KEY-----',
    errorHandler: function(e) {},
    succHandler: function(logItem) {
        var content = logItem.content;
        var logType = logItem.logType;
        var encrypted = logItem.encrypted;
        console.log('Log Succ:' + content);
    }
});
Logan.logWithEncryption('confidentialLogContent', 1);

```

### ð log(content, logType)

* content: æ¥å¿åå®¹ã

* logType: æ¥å¿ç±»åãæ¥å¿ç±»åç¨äºæ¥å¿åç±»ï¼ä¾¿äºä½ å¯¹å·²ä¸æ¥çæ¥å¿åå®¹è¿è¡åç±»æ¥çãä½ å¯ä»¥èªå·±å®ä¹éè¦çæ¥å¿ç±»åã

### ð logWithEncryption(content, logType)

ä½¿ç¨ log() æ¹æ³è½å°çæ¥å¿æ¥è¿äºææå­å¨ï¼ä»»ä½æåæ³è§¦è¾¾è¯¥ç¨æ·ç«¯çäººé½è½å¤è·åå°æ¬å°æ¥å¿ãå¦æä½ ææä¸äºæ¥å¿åå®¹å å¯ååè½å°ï¼ä½ å¯ä»¥è°ç¨è¯¥æ¹æ³ãLogan ä½¿ç¨å¯¹ç§°å å¯ç»åéå¯¹ç§°å å¯çæ¹å¼æ¥ä¿éæ¬å°æ¥å¿å®å¨ãæ¥å¿åå®¹ä¼ä½¿ç¨ AES è¿è¡å å¯ï¼åæ¶ AES å å¯æ¶ä½¿ç¨çå¯¹ç§°å¯é¥ä¼ä½¿ç¨ RSA è¿è¡éå¯¹ç§°å å¯ï¼å å¯åçå¯é¥å¯æä¼åæ¥å¿å¯æä¸èµ·è½å°ä¸æ¥ã

éè¦æ³¨æçæ¯ï¼è½ç¶ä½¿ç¨è¯¥æ¹æ³å­å¨åçæ¥å¿å¾é¾åè¢«ç ´è§£ï¼ä½æ¯ä¸è½ä¿è¯ä½ çæ¥å¿åå®¹å¨å­å¨ä¹åä¸è¢«çªå¬ãå¦å¤ç±äºå¨ç¨æ·ç«¯å å¯ä»¥åå¨æå¡ç«¯è§£å¯é½æ´èè´¹æ¶é´ä¸å¯è½å¼èµ·æ§è½é®é¢ï¼æä»¥å»ºè®®ä½ åªå¨æ¥å¿åå®¹ææçå¿è¦æ¶åä½¿ç¨è¯¥æ¹æ³ã

### ð report(reportConfig)

è¯¥ report() å¼æ­¥æ¹æ³ä¼ä»æ¬å° DB åºä¸­è·åæå®å¤©çæ¥å¿éå¤©è¿è¡ä¸æ¥ãä¸æ¥å®æåä¼è¿åä¸ä¸ªå¤©ä¸º keyï¼ä¸æ¥ç»æä¸º value çå¯¹è±¡ã

* reportConfig: æ¬æ¬¡ä¸æ¥çåæ°å¯¹è±¡ã

	* reportUrl (å¯é): ç¨äºæ¥æ¶æ¬å°ä¸æ¥æ¥å¿åå®¹çæå¡å¨å°åãå¦æä½ å·²éè¿ initConfig() è®¾ç½®äºåæ ·ç reportUrl ä½ä¸ºå¨å±ä¸æ¥å°åï¼è¯¥é¡¹å¯ç¥ã
	
	* deviceId: è¯¥ç¨æ·ç«¯ç¯å¢çå¯ä¸æ è¯ç¬¦ï¼ç¨äºåºåå¶ä»è®¾å¤ç¯å¢ä¸æ¥çæ¥å¿ï¼ä½ éè¦éè¿è¯¥æ è¯ç¬¦å¨æå¡ç«¯æ£ç´¢å·²ä¸æ¥çæ¥å¿ä¿¡æ¯ã
	
	* fromDayString: ä¸æ¥è¯¥å¤©åä¹åçæ¥å¿ï¼YYYY-MM-DD æ ¼å¼ã	
	* toDayString: ä¸æ¥è¯¥å¤©åä¹åçæ¥å¿ï¼YYYY-MM-DD æ ¼å¼.
	
	* webSource (å¯é): å½åä¸æ¥æ¥æºï¼å¦Chromeãå¾®ä¿¡ãQQç­ã
	
	* environment (å¯é): å½åç¯å¢ä¿¡æ¯ï¼å¦å½åUAä¿¡æ¯ç­ã
	
	* customInfo (å¯é): å½åç¨æ·æä¸å¡éå ä¿¡æ¯ã

    * incrementalReport(å¯é): è¥è®¾ä¸ºtrueï¼åæ¬æ¬¡ä¸æ¥ä¸ºå¢éä¸æ¥ï¼ä¸æ¥çæ¥å¿å°ä»æ¬å°å é¤ãé»è®¤ä¸ºfalseã 

    * xhrOptsFormatter(å¯é): å¯è®¾ç½®èªå®ä¹çxhréç½®æ¥è¦çé»è®¤çloganä¸æ¥xhrè®¾ç½®ãä½ å¯ä»¥åèä¸é¢ç¨æ³ç¤ºä¾2ã

ç¨æ³ç¤ºä¾1ï¼

```js
import Logan from 'logan-web';
const reportResult = await Logan.report({
    reportUrl: 'https://yourServerAddressToAcceptLogs',
    deviceId: 'LocalDeviceIdOrUnionId',
    fromDayString: '2019-11-06',
    toDayString: '2019-11-08',
    webSource: 'Chrome',
    environment: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.120 Safari/537.36',
    customInfo: JSON.stringify({userId: 123456, biz: 'Live Better'})
});

console.log(reportResult);
/* e.g.
{ 
	2019-11-06: {msg: "No log exists"},
	2019-11-07: {msg: "Report succ"},
	2019-11-08: {msg: "Report fail", desc: "Server error: 500"}
}
*/
```

ç¨æ³ç¤ºä¾2ï¼
```js
import Logan from 'logan-web';
const reportResult = await Logan.report({
    fromDayString: '2019-11-06',
    toDayString: '2019-11-08',
    /**
    * @param {Function} - logan-webä¼å°æ¬æ¬¡å³å°ä¸æ¥çæ¥å¿ä¿¡æ¯ï¼æ¥å¿å¯¹åºçé¡µæ°ä»¥åä¸æ¥å½å¤©æ¥æä½ä¸ºå¥åæä¾ç»ä½ çformatterï¼ä½ å¯ä»¥è®©formatteræ¥ç»ç»å¹¶è¿åææä¸æ¥çæ°æ®æ ¼å¼åxhråæ°ã
    * @returns {Object} xhrOpts - è¿åxhréç½®å¯¹è±¡
    * @returns {*} xhrOpts.data - dataçç±»åæ¯ä»»æçï¼åªéä½ çæå¡å¨ç«¯è½æ¥æ¶æåå¹¶è§£æå³å¯
    * @returns {boolean} [xhrOpts.withCredentials=false] - å¯éï¼é»è®¤ä¸ºfalse
    * @returns {Object} [xhrOpts.header={
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json,text/javascript'
            }] - å¯éï¼ä½ å¯ä»¥éç½®èªå®ä¹çheaderæ¥æ¿ä»£æé»è®¤çheader
    * @returns {Function=} xhrOpts.responseDealer - å¯éï¼ä½ å¯ä»¥éç½®è¯¥æ¹æ³æ¥èªå®ä¹å¤çæå¡ç«¯responseï¼åªéåè¯logan-webæ¬æ¬¡ä¸æ¥è¢«è®¤ä¸ºæ¯æåè¿æ¯å¤±è´¥ãè¯¥ç»æä¼è¢«logan-webæ¶éå¹¶æç»åæ å¨reportæ¥å£çreportResultä¸­ã
    */
    xhrOptsFormatter: function (logItemStrings, logPageNo/* logPageNo starts from 1 */, logDayString) {
        return {
            reportUrl: 'https://yourServerAddressToAcceptLogs',
            data: {
                fileDate: logDayString,
                logArray: logItemStrings.toString(),
                logPageNo: logPageNo,
                /* ...Other properties you want to post to the server */
            },
            withCredentials: false,
            header: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json,text/javascript'
            },
            responseDealer: function (xhrResponseText) {
                if (xhrResponseText === 'well done') {
                    return {
                        resultMsg: 'Report succ'
                    };
                } else {
                    return {
                        resultMsg: 'Report fail',
                        desc: 'what is wrong with this report'
                    };
                }
            }
        }
    }
});
console.log(reportResult);
/* e.g.
{ 
	2019-11-06: {msg: "No log exists"},
	2019-11-07: {msg: "Report succ"},
	2019-11-08: {msg: "Report fail", desc: "what is wrong with this report"}
}
*/
```

## å®¹éè®¾é
å°½ç®¡ IndexedDB çæ°æ®å®¹éè¾ä¹äºå¶ä»æµè§å¨å­å¨ç©ºé´æ¥è¯´æ¯å¾å¤§äºï¼ä½å®ä¹æ¯æå®¹ééå¶çãIndexedDB çå®¹ééå¶æ¯åéç¦»çï¼æäºæµè§å¨ä¼å¨å½ååä¸ IndexedDB è¶è¿ 50MB æ°æ®ç¨éæ¶å¼¹åºç¨æ·ææå¼¹æ¡æ¥å¼å¯¼ç¨æ·åè®¸æ´å¤§å®¹éçæ¬å°å­å¨ä½¿ç¨ç©ºé´ãä¸ºäºé¿åå½±åç¨æ·ï¼Logan Web å°æå¤åªå­å¨ 7 å¤©æ¥å¿ï¼æ¯å¤©æ¥å¿ééå¶å¨ 7Mãè¾¾å°è¯¥æ¥å¿éåï¼åç»­çå½å¤©æ¥å¿å°ä¸åè½å­å¨æåãè¿ææ¥å¿ä¼å¨ä¸ä¸æ¬¡ log æ¶è¢«æ¸é¤ã

ä¸å¤©çä¸æ¥å¯è½ä¼è¢«æåæå¤ä¸ªå°ä½ç§¯çåé¡µå¹¶ååéãæ¯é¡µæ¿è½½å¤§çº¦ 1MB ä½ç§¯çæ¥å¿éï¼å¨æå¡ç«¯ä¼å°è¿äºåé¡µæ¼æ¥æå®æ´çå½å¤©æ¥å¿ã


## Logan Web SDKçæ´ä½æ¶æ
logan-web æ¯å¨åæ ·å¼æºç [idb-managed](https://github.com/sylvia1106/idb-managed) è¯¥ååºç¡ä¸æ­å»ºçãè¯¥åä¸»è¦è´è´£å¯¹ IndexedDB API çå°è£ä¸è°ç¨ãä»¥ä¸æ¯ logan-web çæ´ä½æ¶æç¤ºæå¾ï¼

<img style="width:70%;" src="https://raw.githubusercontent.com/Meituan-Dianping/Logan/master/Logan/WebSDK/img/logan_web_structure.png"/>


## è½¯ä»¶è®¸å¯åè®®
Logan Web éµç§ [MIT è®¸å¯åè®®](https://github.com/Meituan-Dianping/Logan/blob/master/LICENSE)
