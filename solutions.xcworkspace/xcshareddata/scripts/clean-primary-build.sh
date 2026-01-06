schemes=$( xcodebuild -list \
  | awk '/Schemes:/ {flag=1; next} /^$/ {flag=0} flag' \
  | sed -e 's/^[[:space:]]*//' )

for scheme in $schemes; do
  find ~/Library/Developer/Xcode/DerivedData -maxdepth 5 -name "${scheme}\.*"
done
