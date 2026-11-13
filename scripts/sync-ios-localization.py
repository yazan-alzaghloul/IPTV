#!/usr/bin/env python3
"""Reuse Android translations where the English source label matches."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re, json
root = Path(__file__).resolve().parents[1]
def xml_values(language):
    base = root/'apps/android/app/src/main/res'/language
    result = {}
    for p in base.glob('*.xml'):
        for el in ET.parse(p).getroot().findall('string'):
            result[el.attrib['name']] = ''.join(el.itertext()).replace("\\'", "'").replace('\\"', '"')
    return result
en, ar = xml_values('values'), xml_values('values-ar')
translations = {text: ar[key] for key, text in en.items() if key in ar and '%' not in text}
extra = {
'Preview ready':'المعاينة جاهزة', 'Loading preview':'جارٍ تحميل المعاينة',
'Home':'الرئيسية', 'Live TV':'البث المباشر', 'Movies':'الأفلام', 'Series':'المسلسلات', 'More':'المزيد', 'Search':'بحث', 'Settings':'الإعدادات', 'Favorites':'المفضلة',
'Loading':'جارٍ التحميل', 'Loading your library':'جارٍ تحميل مكتبتك', 'Loading details':'جارٍ تحميل التفاصيل', 'Loading channels':'جارٍ تحميل القنوات', 'Loading guide':'جارٍ تحميل الدليل',
'Your channels. One place.':'قنواتك في مكان واحد.', 'Sign in with your IPTV provider’s Xtream account.':'سجل الدخول بحساب Xtream الخاص بمزود الخدمة.',
'Host or provider URL':'المضيف أو رابط المزود', 'Port (optional)':'المنفذ (اختياري)', 'Username':'اسم المستخدم', 'Password':'كلمة المرور', 'Connect':'اتصال', 'Connecting':'جارٍ الاتصال', 'Protocol':'البروتوكول',
'Your login stays on this device. Channels and titles come from your subscription.':'تبقى بيانات الدخول على هذا الجهاز. تأتي القنوات والعناوين من اشتراكك.',
'HTTP sends your login and video without encryption. Use HTTPS when your provider supports it.':'يرسل HTTP بيانات الدخول والفيديو دون تشفير. استخدم HTTPS عندما يدعمه المزود.',
'Enter your username and password.':'أدخل اسم المستخدم وكلمة المرور.', 'Enter the provider base URL without a playlist, login details, query, or fragment.':'أدخل رابط المزود الأساسي دون رابط قائمة تشغيل أو بيانات دخول أو استعلام.',
'Voice search':'البحث الصوتي', 'Stop voice search':'إيقاف البحث الصوتي', 'Allow microphone and speech recognition in Settings to use voice search.':'اسمح باستخدام الميكروفون والتعرف على الكلام من الإعدادات للبحث الصوتي.',
'Voice search is unavailable. Type your search instead.':'البحث الصوتي غير متاح. اكتب عبارة البحث.', 'Search your library':'ابحث في مكتبتك', 'All categories':'كل الفئات', 'Rated only':'العناوين المقيمة فقط', 'Reset filters':'إعادة ضبط المرشحات',
'Content':'المحتوى', 'Provider order':'ترتيب المزود', 'Title A–Z':'العنوان أبجديًا', 'Date added':'تاريخ الإضافة', 'Release date':'تاريخ الإصدار', 'Provider rating':'تقييم المزود', 'titles':'عنوانًا', 'Browse':'تصفح',
'Continue series':'متابعة المسلسل', 'Play now':'تشغيل الآن', 'Details':'التفاصيل', 'Saved':'محفوظ', 'Watchlist':'قائمة المشاهدة', 'Favorite':'مفضل', 'Toggle favorite':'تبديل المفضلة', 'Viewing progress':'تقدم المشاهدة',
'Channel guide':'دليل القناة', 'Guide and preview':'الدليل والمعاينة', 'On this channel':'على هذه القناة', 'No matching channels':'لا توجد قنوات مطابقة', 'Select channel':'اختيار القناة', 'Schedule timeline':'الجدول الزمني', 'Current time':'الوقت الحالي',
'No watch history':'لا يوجد سجل مشاهدة', 'Remove':'إزالة', 'min':'دقيقة', 'Playback diagnostics':'تشخيص التشغيل', 'Buffering':'جارٍ التخزين المؤقت', 'Fit':'ملاءمة', 'Fill':'ملء', 'Stretch':'تمديد', 'Up to':'حتى', 'Autoplay':'تشغيل تلقائي',
'This stream is unavailable or unsupported on this device. Try HLS or another title.':'هذا البث غير متاح أو غير مدعوم على هذا الجهاز. جرب HLS أو عنوانًا آخر.', 'Connection interrupted. Reconnecting…':'انقطع الاتصال. جارٍ إعادة الاتصال…',
'Connection interrupted. Check your network, then retry.':'انقطع الاتصال. تحقق من الشبكة ثم أعد المحاولة.', 'Try HLS':'جرب HLS', 'Try MPEG-TS':'جرب MPEG-TS',
'Clear watch history?':'هل تريد مسح سجل المشاهدة؟', 'Sign out and remove saved account data?':'هل تريد تسجيل الخروج وإزالة بيانات الحساب المحفوظة؟', 'Sign out':'تسجيل الخروج',
'Your device’s system text size is also respected.':'يتم أيضًا احترام حجم النص في إعدادات الجهاز.', 'Audio, subtitles and Picture in Picture use the system video controls.':'استخدم عناصر تحكم الفيديو لاختيار الصوت والترجمة ووضع صورة داخل صورة.',
'Provider credentials are excluded from appearance exports.':'لا تتضمن ملفات تصدير المظهر بيانات حساب المزود.', 'Some provider formats and codecs require an HLS or MP4 alternative on iOS.':'تتطلب بعض تنسيقات المزود وترميزاته بديلاً بصيغة HLS أو MP4 على iOS.',
'Network':'الشبكة', 'Connected':'متصل', 'Offline':'غير متصل', 'Version':'الإصدار', 'Device memory':'ذاكرة الجهاز', 'Storage unavailable':'التخزين غير متاح', 'Check available device storage and reopen IPTV.':'تحقق من مساحة التخزين المتاحة وأعد فتح التطبيق.',
'Could not save viewing progress. Check device storage.':'تعذر حفظ تقدم المشاهدة. تحقق من مساحة التخزين.', 'Port must be a number between 1 and 65535.':'يجب أن يكون المنفذ رقمًا بين 1 و65535.',
'Could not complete this request. Check your connection and try again.':'تعذر إكمال الطلب. تحقق من الاتصال وأعد المحاولة.', 'Saved login could not be unlocked. Please sign in again.':'تعذر فتح بيانات الدخول المحفوظة. سجل الدخول مجددًا.',
'Could not clear saved data. Try signing out again.':'تعذر مسح البيانات المحفوظة. حاول تسجيل الخروج مجددًا.', 'Refresh to load content from your provider.':'حدّث لتحميل المحتوى من مزود الخدمة.',
'Preview unavailable. Try fullscreen playback.':'المعاينة غير متاحة. جرب التشغيل بملء الشاشة.', 'Try another filter or refresh your provider.':'جرب مرشحًا آخر أو حدّث بيانات المزود.',
'No episodes available from this provider.':'لا توجد حلقات متاحة من هذا المزود.', 'This also resets saved progress for titles in your recent history. Favorites stay saved.':'سيتم أيضًا إعادة ضبط تقدم المشاهدة في السجل الأخير. تبقى المفضلة محفوظة.',
}
translations.update(extra)
keys=set(translations)
for p in (root/'apps/ios').rglob('*.swift'):
    if '.build' not in str(p): keys.update(re.findall(r'localized\("([^"\\]*)"\)',p.read_text()))
# Include domain/provider errors, which are localized when presented.
for p in (root/'apps/ios/Core/Sources/IPTVCore').glob('*.swift'):
    keys.update(re.findall(r'IPTVError.message\("([^"\\]*)"\)',p.read_text()))
for language in ['en','ar']:
    directory=root/f'apps/ios/Resources/{language}.lproj'; directory.mkdir(exist_ok=True)
    (directory/'Localizable.strings').write_text('\n'.join(f'{json.dumps(key,ensure_ascii=False)} = {json.dumps(translations.get(key,key) if language=="ar" else key,ensure_ascii=False)};' for key in sorted(keys))+'\n')
    privacy={'NSMicrophoneUsageDescription':'استخدم الميكروفون عند اختيار البحث الصوتي للعثور على عناوين في مكتبتك.', 'NSSpeechRecognitionUsageDescription':'حوّل البحث المنطوق إلى نص للعثور على عناوين في مكتبة المزود.'}
    (directory/'InfoPlist.strings').write_text('\n'.join(f'{json.dumps(k)} = {json.dumps(v,ensure_ascii=False)};' for k,v in privacy.items())+'\n' if language=='ar' else '')
