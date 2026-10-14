# IRNoti Android SDK

[![JitPack](https://jitpack.io/v/irnoti/andriod-sdk.svg)](https://jitpack.io/#irnoti/andriod-sdk)

<div dir="rtl" align="right">

SDK رسمی ایران‌ناتیفیکیشن (IRNoti) برای اپلیکیشن‌های Android است. با آن می‌توانید دستگاه را ثبت کنید، اعلان Firebase را دریافت و نمایش دهید، کاربران را با Topic، Tag، External ID و Alias دسته‌بندی کنید، رویدادهای رفتاری را بفرستید و گزارش تحویل و کلیک اعلان را ثبت کنید.

نسخه فعلی: **1.0.0**

## فهرست

- [قابلیت‌ها](#قابلیتها)
- [پیش‌نیازها](#پیشنیازها)
- [راه‌اندازی سریع](#راهاندازی-سریع)
- [نصب](#نصب)
- [اتصال Firebase](#اتصال-firebase)
- [راه‌اندازی SDK](#راهاندازی-sdk)
- [مجوز اعلان در Android 13+](#مجوز-اعلان-در-android-13-و-بالاتر)
- [ارسال اولین اعلان](#ارسال-اولین-اعلان)
- [متدهای عمومی](#متدهای-عمومی)
- [Topic](#topic)
- [External ID و Alias](#external-id-و-alias)
- [Tag](#tag)
- [رویدادها](#رویدادها)
- [لینک، Deep Link و دکمه‌ها](#لینک-deep-link-و-دکمههای-اعلان)
- [اگر اپ شما سرویس Firebase دارد](#اگر-اپ-شما-سرویس-firebase-دارد)
- [استفاده در Java](#استفاده-در-java)
- [رفع اشکال](#رفع-اشکال)

## قابلیت‌ها

| قابلیت | توضیح |
|---|---|
| ثبت خودکار دستگاه | دریافت توکن FCM، ثبت آن در ایران‌ناتیفیکیشن و به‌روزرسانی خودکار هنگام تغییر توکن |
| نمایش اعلان | عنوان، متن، تصویر بزرگ و آیکن؛ در حالت Foreground و Background |
| دکمه‌های اعلان | حداکثر دو دکمه عملیاتی |
| لینک و Deep Link | لینک وب و Deep Link (Custom Scheme یا App Link) |
| Topic | عضویت و لغو عضویت و دریافت فهرست Topicهای دستگاه |
| External ID و Alias | اتصال دستگاه به شناسه کاربر خودتان و چند شناسه برچسب‌دار مثل `phone` |
| Tag | برچسب‌های ساده برای بخش‌بندی (مثل `plan=pro`) |
| رویداد | ارسال رویدادهای رفتاری همراه با پارامتر |
| گزارش | ثبت تحویل، کلیک، بستن و دکمه با صف ماندگار و تلاش مجدد خودکار |
| Kotlin و Java | API عمومی برای هر دو زبان |

## پیش‌نیازها

1. یک **سازمان** و یک **پروژه** در ایران‌ناتیفیکیشن که سرویس «پوش‌نوتیفیکیشن» برایش فعال شده است.
2. یک **اپ** در همان سرویس (با فایل JSON حساب سرویس Firebase). بعد از ساخت، **کلید عمومی** اپ (`pk_live_…`) را از پنل بردارید.
3. یک پروژه Firebase و یک Android App در آن با Package Name دقیق اپ شما، و فایل `google-services.json` همان Android App.
4. Android 6.0 یا بالاتر (`minSdk 23`) و `compileSdk 35` یا بالاتر.

> [!IMPORTANT]
> پروژه Firebase اپ و پروژه Firebase‌ای که حساب سرویسش را در ایران‌ناتیفیکیشن ثبت کرده‌اید باید یکی باشند.

> [!CAUTION]
> فقط **کلید عمومی** (`pk_live_…`) را داخل اپ بگذارید. فایل حساب سرویس Firebase و لینک بروکر محرمانه‌اند و نباید داخل APK، سورس اپ یا GitHub قرار بگیرند.

## راه‌اندازی سریع

1. Dependency را از JitPack اضافه کنید.
2. `google-services.json` را در پوشه `app/` بگذارید و پلاگین Google Services را فعال کنید.
3. در `Application.onCreate()` متد `Irnoti.init()` را با کلید عمومی صدا بزنید.
4. در Android 13+ مجوز اعلان را بگیرید.
5. اپ را روی دستگاه واقعی اجرا کنید و از پنل ایران‌ناتیفیکیشن یک پیام آزمایشی بفرستید.

## نصب

در `settings.gradle.kts`:

</div>

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

<div dir="rtl" align="right">

در `app/build.gradle.kts`:

</div>

```kotlin
dependencies {
    implementation("com.github.irnoti:andriod-sdk:1.0.0")
}
```

<div dir="rtl" align="right">

> نام مخزن `andriod-sdk` است (با همین املا) و artifact هم همین نام را دارد.

## اتصال Firebase

اگر اپ شما Firebase دارد این مرحله انجام شده است. در غیر این صورت:

</div>

```kotlin
// build.gradle.kts (root)
plugins { id("com.google.gms.google-services") version "4.4.2" apply false }

// app/build.gradle.kts
plugins { id("com.google.gms.google-services") }
```

<div dir="rtl" align="right">

## راه‌اندازی SDK

در کلاس `Application` (و در `AndroidManifest.xml` به آن اشاره کنید):

</div>

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Irnoti.init(
            this,
            IrnotiConfig(
                appKey = "pk_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                smallIcon = R.drawable.ic_stat_notify, // آیکن تک‌رنگ
            ),
        )
    }
}
```

<div dir="rtl" align="right">

`init` فقط یک بار در شروع پروسه لازم است. ثبت دستگاه در پس‌زمینه انجام می‌شود و خطای شبکه اپ را نمی‌شکند؛ در شروع بعدی اپ دوباره تلاش می‌شود.

## مجوز اعلان در Android 13 و بالاتر

مجوز `POST_NOTIFICATIONS` را خودتان از کاربر بگیرید (از داخل یک Activity) و نتیجه را به SDK بگویید:

</div>

```kotlin
private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    Irnoti.setNotificationsEnabled(granted)
}

fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
}
```

<div dir="rtl" align="right">

## ارسال اولین اعلان

از پنل ایران‌ناتیفیکیشن (بخش پوش پروژه) یا از سرور خودتان با کلید API پروژه:

</div>

```bash
curl -X POST "https://api.irnoti.ir/v1/sdk-apps/APP_ID/send" \
  -H "Authorization: Bearer irn_live_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "message": { "title": "سلام", "body": "اولین اعلان شما", "url": "https://example.com" },
    "target": { "all": true }
  }'
```

<div dir="rtl" align="right">

## متدهای عمومی

| متد | توضیح |
|---|---|
| `Irnoti.init(context, config)` | راه‌اندازی و ثبت دستگاه |
| `Irnoti.setNotificationsEnabled(Boolean)` | اطلاع از وضعیت مجوز اعلان |
| `Irnoti.identify(externalId, aliases)` | اتصال دستگاه به کاربر شما |
| `Irnoti.logout()` | جدا کردن کاربر از دستگاه |
| `Irnoti.subscribe(topic)` / `unsubscribe(topic)` | عضویت و لغو عضویت در Topic |
| `Irnoti.setTags(map)` | تنظیم یا حذف (مقدار `null`) Tag |
| `Irnoti.track(name, props)` | ارسال رویداد |
| `Irnoti.unregister()` | لغو کامل ثبت دستگاه |

همه متدها غیرهمزمان‌اند و قبل از ثبت دستگاه هم امن هستند (در صف قرار می‌گیرند).

## Topic

</div>

```kotlin
Irnoti.subscribe("news")
Irnoti.unsubscribe("news")
```

<div dir="rtl" align="right">

نام Topic: حروف انگلیسی، عدد و `_ . -` تا ۶۴ نویسه؛ حداکثر ۱۰۰ Topic برای هر دستگاه.

## External ID و Alias

بعد از ورود کاربر:

</div>

```kotlin
Irnoti.identify("user-123", mapOf("phone" to "09120000000", "tier" to "gold"))
// هنگام خروج:
Irnoti.logout()
```

<div dir="rtl" align="right">

## Tag

</div>

```kotlin
Irnoti.setTags(mapOf("plan" to "pro", "city" to "tehran", "old_tag" to null)) // null = حذف
```

<div dir="rtl" align="right">

حداکثر ۵۰ Tag؛ کلید تا ۴۰ و مقدار تا ۲۵۶ نویسه.

## رویدادها

</div>

```kotlin
Irnoti.track("purchase", mapOf("amount" to 120000, "currency" to "IRR"))
```

<div dir="rtl" align="right">

پارامترها (`props`) حداکثر ۴ کیلوبایت‌اند.

## لینک، Deep Link و دکمه‌های اعلان

فیلدهای پیام (در ارسال از پنل یا API):

| فیلد | توضیح |
|---|---|
| `title`, `body` | عنوان و متن |
| `image` | آدرس `https` تصویر بزرگ |
| `url` | لینک وب که با لمس اعلان باز می‌شود |
| `deeplink` | آدرس مطلق با Scheme اپ (مثل `myapp://orders/5`)؛ بر `url` اولویت دارد |
| `actions` | حداکثر دو دکمه: `[{"id":"open","title":"مشاهده","url":"myapp://orders/5"}]` |
| `channel` | شناسه کانال اعلان اندروید |
| `extra` | داده سفارشی به‌صورت کلید-مقدار رشته‌ای |

برای Deep Link، Intent Filter مربوط به Scheme را در Activity مقصد تعریف کنید.

## اگر اپ شما سرویس Firebase دارد

سرویس SDK به‌صورت خودکار در Manifest کتابخانه ثبت می‌شود. اگر خودتان `FirebaseMessagingService` دارید، سرویس خودتان را اعلام کنید و این سه فراخوانی را اضافه کنید:

</div>

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Irnoti.onNewToken(token)
        // کد شما
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (Irnoti.isIrnotiMessage(message.data)) Irnoti.handleMessage(this, message.data)
        else { /* پیام‌های خودتان */ }
    }
}
```

<div dir="rtl" align="right">

## استفاده در Java

</div>

```java
Irnoti.init(this, new IrnotiConfig("pk_live_xxx", R.drawable.ic_stat_notify));
Irnoti.identify("user-123");
Irnoti.subscribe("news");
```

<div dir="rtl" align="right">

## رفع اشکال

| مشکل | علت و راه‌حل |
|---|---|
| دستگاه در پنل دیده نمی‌شود | کلید عمومی را بررسی کنید؛ اپ باید در سرویس فعال پروژه ساخته شده باشد. اینترنت و دسترسی به `api.irnoti.ir` را بررسی کنید. |
| ثبت با خطای ۴۰۳ رد می‌شود | سرویس پوش پروژه غیرفعال/منقضی شده یا سقف دستگاه بسته فعلی پر شده است؛ وضعیت را در پنل ببینید. |
| اعلان نمی‌رسد | پروژه Firebase اپ و حساب سرویس ثبت‌شده باید یکی باشند. مجوز اعلان (Android 13+) و Battery Optimization را بررسی کنید. |
| اعلان نمایش داده نمی‌شود ولی ارسال موفق است | `smallIcon` باید یک drawable تک‌رنگ معتبر باشد. |
| لینک یا Deep Link باز نمی‌شود | SDK کلیک را با یک Activity واسط ثبت و بعد لینک را باز می‌کند؛ مطمئن شوید Scheme در یک Activity مقصد با Intent Filter تعریف شده است. |

## امنیت

- فقط کلید عمومی داخل اپ قرار می‌گیرد؛ توکن دستگاه (`dt_…`) در حافظه خصوصی اپ ذخیره می‌شود. برای اپ‌های حساس `android:allowBackup="false"` بگذارید.
- ارتباط فقط با `https` است.

## مجوز استفاده

مجوز استفاده را مالک مخزن تعیین می‌کند (فایل `LICENSE`).

</div>
