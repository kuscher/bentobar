#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
C=$HOME/.cache/android
OUT=build; rm -rf $OUT; mkdir -p $OUT/gen $OUT/classes $OUT/dex
aapt2 compile --dir res -o $OUT/res.zip
aapt2 link -o $OUT/app.apk -I "$C/android.jar" --manifest AndroidManifest.xml --java $OUT/gen $OUT/res.zip
javac --release 17 -Xlint:all,-options -encoding UTF-8 -cp "$C/android-37.jar" -d $OUT/classes $(find src $OUT/gen -name '*.java')
java -cp "$C/r8.jar" com.android.tools.r8.D8 --release --min-api 34 --lib "$C/android-37.jar" --output $OUT/dex $(find $OUT/classes -name '*.class')
python3 -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a',zipfile.ZIP_DEFLATED); z.write(sys.argv[2],'classes.dex')" $OUT/app.apk $OUT/dex/classes.dex
zipalign -f 4 $OUT/app.apk $OUT/aligned.apk
[[ -f barprobe.jks ]] || keytool -genkeypair -keystore barprobe.jks -storepass barprobe -keypass barprobe -alias key -keyalg RSA -keysize 2048 -validity 365 -dname "CN=BentoBar probe" 2>/dev/null
apksigner sign --ks barprobe.jks --ks-pass pass:barprobe --key-pass pass:barprobe --out barprobe.apk $OUT/aligned.apk
rm -f barprobe.apk.idsig; ls -la barprobe.apk
