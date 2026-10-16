PLANNER X — Android APK Build Project

यह ZIP APK नहीं है। यह Android project है जिससे GitHub Actions के जरिए debug APK बनाया जा सकता है।

APK बनाने के चरण:
1. GitHub पर नया repository बनाओ, नाम: Planner-X
2. इस ZIP को extract करो और इसके अंदर की सभी files/folders repository में upload करो। .github folder भी upload होना चाहिए।
3. Commit changes करके main branch में डालो।
4. Repository में Actions tab खोलो।
5. "Build Planner X APK" workflow चुनो और Run workflow दबाओ (या push के बाद build अपने-आप चलेगा)।
6. Build सफल होने पर workflow run खोलो, नीचे Artifacts में Planner-X-debug-APK डाउनलोड करो।
7. ZIP artifact को extract करके app-debug.apk फोन में डाउनलोड करो और install करो। Android पूछे तो browser/file manager को "Install unknown apps" की अनुमति देना।

सीमाएँ:
- यह पहला Android wrapper है: Planner X का मौजूदा HTML ऐप Android WebView में खुलेगा।
- APK को यहाँ compile/test नहीं किया गया; GitHub Actions build result की पुष्टि जरूरी है।
- यह version अभी native full-screen reminders / alarm permissions implement नहीं करता। मौजूदा web reminder code की क्षमता Android द्वारा सीमित हो सकती है। उन features के लिए अगला development step चाहिए।
- App data local browser storage में रहता है; app data clear/uninstall करने से data खो सकता है। JSON backup का इस्तेमाल करो।
