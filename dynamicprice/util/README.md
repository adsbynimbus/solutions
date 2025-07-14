# Dynamic Price Util

## Setup

Uses Google Application Default Credentials

```shell
gcloud auth application-default login --scopes=https://www.googleapis.com/auth/admanager,https://www.googleapis.com/auth/cloud-platform --disable-quota-project --no-launch-browser
```

## Running the script

#### Parameters

- dynamicprice.util.appname = Application name created in the Google cloud console
- dynamicprice.util.network = Network code to apply Dynamic Price Setup
- dynamicprice.util.orderid = Order ID to reconfigure with lines from $50-$125

```shell
./gradlew :dynamicprice:util:jvmRun -Pdynamicprice.util.appname= -Pdynamicprice.util.networkcode= -Pdynamicprice.util.orderid=
```
