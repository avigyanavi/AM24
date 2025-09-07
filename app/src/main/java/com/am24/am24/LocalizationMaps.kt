package com.am24.am24

import android.content.Context
import java.util.Calendar

// ─── Basic Info ────────────────────────────────────
object LocalizationMaps {
    //── 1) How to format your “joined on” date string ──
    val joinedOnDateFormat = mapOf(
        //          ① monthName      ② day          ③ year
        "en" to "%1\$s %2\$02d, %3\$d",           // January 01, 2025
        "bn" to "%2\$02d %1\$s, %3\$d",           // 01 জানুয়ারি, 2025
        "hi" to "%2\$02d %1\$s, %3\$d",           // 01 जनवरी, 2025
        "es" to "%2\$02d de %1\$s de %3\$d",      // 01 de enero de 2025
        "ta" to "%2\$02d %1\$s, %3\$d",
        "or" to "%2\$02d %1\$s, %3\$d",
        "te" to "%2\$02d %1\$s, %3\$d",
        "mr" to "%2\$02d %1\$s, %3\$d",
        "gu" to "%2\$02d %1\$s, %3\$d",
        "kn" to "%2\$02d %1\$s, %3\$d",
        "ml" to "%2\$02d %1\$s, %3\$d",
        "as" to "%2\$02d %1\$s, %3\$d",
        "pa" to "%2\$02d %1\$s, %3\$d"
    )


    val monthNames = mapOf(
        "en" to listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        ),
        "es" to listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        ),
        "bn" to listOf(
            "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
        ),
        "hi" to listOf(
            "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
            "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
        ),
        "ta" to listOf(
            "ஜனவரி", "பிப்ரவரி", "மார்ச்", "ஏப்ரல்", "மே", "ஜூன்",
            "ஜூலை", "ஆகஸ்ட்", "செப்டம்பர்", "அக்டோபர்", "நவம்பர்", "டிசம்பர்"
        ),
        "or" to listOf(
            "ଜାନୁଆରୀ", "ଫେବୃଆରୀ", "ମାର୍ଚ୍ଚ", "ଏପ୍ରିଲ", "ମଇ", "ଜୁନ",
            "ଜୁଲାଇ", "ଅଗଷ୍ଟ", "ସେପ୍ଟେମ୍ବର", "ଅକ୍ଟୋବର", "ନଭେମ୍ବର", "ଡିସେମ୍ବର"
        ),
        "te" to listOf(
            "జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్",
            "జులై", "ఆగస్టు", "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్"
        ),
        "mr" to listOf(
            "जानेवारी", "फेब्रुवारी", "मार्च", "एप्रिल", "मे", "जून",
            "जुलै", "ऑगस्ट", "सप्टेंबर", "ऑक्टोबर", "नोव्हेंबर", "डिसेंबर"
        ),
        "gu" to listOf(
            "જાન્યુઆરી", "ફેબ્રુઆરી", "માર્ચ", "એપ્રિલ", "મે", "જૂન",
            "જુલાઈ", "ઓગસ્ટ", "સપ્ટેમ્બર", "ઓક્ટોબર", "નવેમ્બર", "ડિસેમ્બર"
        ),
        "kn" to listOf(
            "ಜನವರಿ", "ಫೆಬ್ರವರಿ", "ಮಾರ್ಚ್", "ಏಪ್ರಿಲ್", "ಮೇ", "ಜೂನ್",
            "ಜುಲೈ", "ಆಗಸ್ಟ್", "ಸೆಪ್ಟೆಂಬರ್", "ಅಕ್ಟೋಬರ್", "ನವೆಂಬರ್", "ಡಿಸೆಂಬರ್"
        ),
        "ml" to listOf(
            "ജനുവരി", "ഫെബ്രുവരി", "മാർച്ച്", "ഏപ്രിൽ", "മേയ്", "ജൂൺ",
            "ജൂലൈ", "ഓഗസ്റ്റ്", "സെപ്റ്റംബർ", "ഒക്ടോബർ", "നവംബർ", "ഡിസംബർ"
        ),
        "as" to listOf(
            "জানুৱাৰী", "ফেব্ৰুৱাৰী", "মাৰ্চ", "এপ্ৰিল", "মে", "জুন",
            "জুলাই", "আগষ্ট", "ছেপ্তেম্বৰ", "অক্টোবৰ", "নৱেম্বৰ", "ডিচেম্বৰ"
        ),
        "pa" to listOf(
            "ਜਨਵਰੀ", "ਫਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ",
            "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ"
        )
    )
}

fun canonicalGenderRes(name: String?): Int? {
    if (name.isNullOrBlank()) return null
    genderNameToRes[name]?.let { return it }
    return genderNameToRes.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}


fun formatJoinedOn(
    context: Context,
    timestamp: Long
): String {
    val locale = context.resources.configuration.locales[0].language
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val year  = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)    // 0‐11
    val day   = cal.get(Calendar.DAY_OF_MONTH)

    val monthName = LocalizationMaps.monthNames[locale]?.get(month)
        ?: LocalizationMaps.monthNames["en"]!![month]

    val fmt = LocalizationMaps.joinedOnDateFormat[locale]
        ?: LocalizationMaps.joinedOnDateFormat["en"]!!

    // note: our formats expect "%s %02d, %d" or "%02d %s %d"
    return String.format(fmt, monthName, day, year)
}

val casteNameToRes = mapOf(
    // Brahmin
    "Brahmin" to R.string.caste_brahmin,
    "ব্রাহ্মণ" to R.string.caste_brahmin, // Bengali
    "ब्राह्मण" to R.string.caste_brahmin, // Hindi
    "பிராமணர்" to R.string.caste_brahmin, // Tamil
    "ବ୍ରାହ୍ମଣ" to R.string.caste_brahmin, // Odia
    "బ్రాహ్మణ" to R.string.caste_brahmin, // Telugu
    "ब्राह्मण" to R.string.caste_brahmin, // Marathi
    "બ્રાહ્મણ" to R.string.caste_brahmin, // Gujarati
    "ಬ್ರಾಹ್ಮಣ" to R.string.caste_brahmin, // Kannada
    "ബ്രാഹ്മണ" to R.string.caste_brahmin, // Malayalam
    "ব্ৰাহ্মণ" to R.string.caste_brahmin, // Assamese
    "ਬ੍ਰਾਹਮਣ" to R.string.caste_brahmin, // Punjabi

    // Either
    "Either" to R.string.gender_either,
    "যেকোনো একটি" to R.string.gender_either, // Bengali
    "कोई भी" to R.string.gender_either, // Hindi
    "எதுவும்" to R.string.gender_either, // Tamil
    "ଯେକୌଣସି" to R.string.gender_either, // Odia
    "ఏదైనా" to R.string.gender_either, // Telugu
    "कोणतेही" to R.string.gender_either, // Marathi
    "કંઈપણ" to R.string.gender_either, // Gujarati
    "ಯಾವುದೇ" to R.string.gender_either, // Kannada
    "ഏതെങ്കിലും" to R.string.gender_either, // Malayalam
    "যেকোনো এটা" to R.string.gender_either, // Assamese
    "ਕੋਈ ਵੀ" to R.string.gender_either, // Punjabi

    // Kayastha
    "Kayastha" to R.string.caste_kayastha,
    "কায়স্থ" to R.string.caste_kayastha, // Bengali
    "कायस्थ" to R.string.caste_kayastha, // Hindi
    "காயஸ்தர்" to R.string.caste_kayastha, // Tamil
    "କାୟସ୍ଥ" to R.string.caste_kayastha, // Odia
    "కాయస్థ" to R.string.caste_kayastha, // Telugu
    "कायस्थ" to R.string.caste_kayastha, // Marathi
    "કાયસ્થ" to R.string.caste_kayastha, // Gujarati
    "ಕಾಯಸ್ಥ" to R.string.caste_kayastha, // Kannada
    "കായസ്ഥ" to R.string.caste_kayastha, // Malayalam
    "কায়স্থ" to R.string.caste_kayastha, // Assamese
    "ਕਾਯਸਥ" to R.string.caste_kayastha, // Punjabi

    // Baidya
    "Baidya" to R.string.caste_baidya,
    "বৈদ্য" to R.string.caste_baidya, // Bengali
    "बैद्य" to R.string.caste_baidya, // Hindi
    "பைத்யர்" to R.string.caste_baidya, // Tamil
    "ବୈଦ୍ୟ" to R.string.caste_baidya, // Odia
    "బైద్య" to R.string.caste_baidya, // Telugu
    "बैद्य" to R.string.caste_baidya, // Marathi
    "બૈદ્ય" to R.string.caste_baidya, // Gujarati
    "ಬೈದ್ಯ" to R.string.caste_baidya, // Kannada
    "ബൈദ്യ" to R.string.caste_baidya, // Malayalam
    "বৈদ্য" to R.string.caste_baidya, // Assamese
    "ਬੈਦਯ" to R.string.caste_baidya, // Punjabi

    // Kshatriya
    "Kshatriya" to R.string.caste_kshatriya,
    "ক্ষত্রিয়" to R.string.caste_kshatriya, // Bengali
    "क्षत्रिय" to R.string.caste_kshatriya, // Hindi
    "க்ஷத்திரியர்" to R.string.caste_kshatriya, // Tamil
    "କ୍ଷତ୍ରିୟ" to R.string.caste_kshatriya, // Odia
    "క్షత్రియ" to R.string.caste_kshatriya, // Telugu
    "क्षत्रिय" to R.string.caste_kshatriya, // Marathi
    "ક્ષત્રિય" to R.string.caste_kshatriya, // Gujarati
    "ಕ್ಷತ್ರಿಯ" to R.string.caste_kshatriya, // Kannada
    "ക്ഷത്രിയ" to R.string.caste_kshatriya, // Malayalam
    "ক্ষত্ৰিয়" to R.string.caste_kshatriya, // Assamese
    "ਖਤਰੀ" to R.string.caste_kshatriya, // Punjabi

    // Vaishya
    "Vaishya" to R.string.caste_vaishya,
    "বৈশ্য" to R.string.caste_vaishya, // Bengali
    "वैश्य" to R.string.caste_vaishya, // Hindi
    "வைசியர்" to R.string.caste_vaishya, // Tamil
    "ବୈଶ୍ୟ" to R.string.caste_vaishya, // Odia
    "వైశ్య" to R.string.caste_vaishya, // Telugu
    "वैश्य" to R.string.caste_vaishya, // Marathi
    "વૈશ્ય" to R.string.caste_vaishya, // Gujarati
    "ವೈಶ್ಯ" to R.string.caste_vaishya, // Kannada
    "വൈശ്യ" to R.string.caste_vaishya, // Malayalam
    "বৈশ্য" to R.string.caste_vaishya, // Assamese
    "ਵੈਸ਼ਯ" to R.string.caste_vaishya, // Punjabi

    // Rajvanshi
    "Rajvanshi" to R.string.caste_rajvanshi,
    "রাজবংশী" to R.string.caste_rajvanshi, // Bengali
    "राजवंशी" to R.string.caste_rajvanshi, // Hindi
    "ராஜவம்சி" to R.string.caste_rajvanshi, // Tamil
    "ରାଜବଂଶୀ" to R.string.caste_rajvanshi, // Odia
    "రాజవంశి" to R.string.caste_rajvanshi, // Telugu
    "राजवंशी" to R.string.caste_rajvanshi, // Marathi
    "રાજવંશી" to R.string.caste_rajvanshi, // Gujarati
    "ರಾಜವಂಶಿ" to R.string.caste_rajvanshi, // Kannada
    "രാജവംശി" to R.string.caste_rajvanshi, // Malayalam
    "ৰাজবংশী" to R.string.caste_rajvanshi, // Assamese
    "ਰਾਜਵੰਸ਼ੀ" to R.string.caste_rajvanshi, // Punjabi

    // Sadgop
    "Sadgop" to R.string.caste_sadgop,
    "সদগোপ" to R.string.caste_sadgop, // Bengali
    "सदगोप" to R.string.caste_sadgop, // Hindi
    "சத்கோப்" to R.string.caste_sadgop, // Tamil
    "ସଦଗୋପ" to R.string.caste_sadgop, // Odia
    "సద్గోప్" to R.string.caste_sadgop, // Telugu
    "सदगोप" to R.string.caste_sadgop, // Marathi
    "સદગોપ" to R.string.caste_sadgop, // Gujarati
    "ಸದ್ಗೋಪ್" to R.string.caste_sadgop, // Kannada
    "സദ്ഗോപ്" to R.string.caste_sadgop, // Malayalam
    "সদগোপ" to R.string.caste_sadgop, // Assamese
    "ਸਦਗੋਪ" to R.string.caste_sadgop, // Punjabi

    // Mahishya
    "Mahishya" to R.string.caste_mahishya,
    "মহিষ্য" to R.string.caste_mahishya, // Bengali
    "महिष्य" to R.string.caste_mahishya, // Hindi
    "மஹிஷ்யா" to R.string.caste_mahishya, // Tamil
    "ମହିଷ୍ୟ" to R.string.caste_mahishya, // Odia
    "మహిష్య" to R.string.caste_mahishya, // Telugu
    "महिष्य" to R.string.caste_mahishya, // Marathi
    "મહિષ્ય" to R.string.caste_mahishya, // Gujarati
    "ಮಹಿಷ್ಯ" to R.string.caste_mahishya, // Kannada
    "മഹിഷ്യ" to R.string.caste_mahishya, // Malayalam
    "মহিষ্য" to R.string.caste_mahishya, // Assamese
    "ਮਹਿਸ਼ਯ" to R.string.caste_mahishya, // Punjabi

    // Jat
    "Jat" to R.string.caste_jat,
    "জাট" to R.string.caste_jat, // Bengali
    "जाट" to R.string.caste_jat, // Hindi
    "ஜாட்" to R.string.caste_jat, // Tamil
    "ଜାଟ" to R.string.caste_jat, // Odia
    "జాట్" to R.string.caste_jat, // Telugu
    "जाट" to R.string.caste_jat, // Marathi
    "જાટ" to R.string.caste_jat, // Gujarati
    "ಜಾಟ್" to R.string.caste_jat, // Kannada
    "ജാട്" to R.string.caste_jat, // Malayalam
    "জাট" to R.string.caste_jat, // Assamese
    "ਜਾਟ" to R.string.caste_jat, // Punjabi

    // Rajput
    "Rajput" to R.string.caste_rajput,
    "রাজপুত" to R.string.caste_rajput, // Bengali
    "राजपूत" to R.string.caste_rajput, // Hindi
    "ராஜபுத்திரர்" to R.string.caste_rajput, // Tamil
    "ରାଜପୁତ" to R.string.caste_rajput, // Odia
    "రాజపుత్" to R.string.caste_rajput, // Telugu
    "राजपूत" to R.string.caste_rajput, // Marathi
    "રાજપૂત" to R.string.caste_rajput, // Gujarati
    "ರಾಜಪೂತ್" to R.string.caste_rajput, // Kannada
    "രാജ്പുത്" to R.string.caste_rajput, // Malayalam
    "ৰাজপুত" to R.string.caste_rajput, // Assamese
    "ਰਾਜਪੂਤ" to R.string.caste_rajput, // Punjabi

    // Yadav
    "Yadav" to R.string.caste_yadav,
    "যাদব" to R.string.caste_yadav, // Bengali
    "यादव" to R.string.caste_yadav, // Hindi
    "யாதவர்" to R.string.caste_yadav, // Tamil
    "ଯାଦବ" to R.string.caste_yadav, // Odia
    "యాదవ్" to R.string.caste_yadav, // Telugu
    "यादव" to R.string.caste_yadav, // Marathi
    "યાદવ" to R.string.caste_yadav, // Gujarati
    "ಯಾದವ್" to R.string.caste_yadav, // Kannada
    "യാദവ്" to R.string.caste_yadav, // Malayalam
    "যাদৱ" to R.string.caste_yadav, // Assamese
    "ਯਾਦਵ" to R.string.caste_yadav, // Punjabi

    // Vellalar
    "Vellalar" to R.string.caste_vellalar,
    "ভেল্লালার" to R.string.caste_vellalar, // Bengali
    "वेल्लालर" to R.string.caste_vellalar, // Hindi
    "வெள்ளாளர்" to R.string.caste_vellalar, // Tamil
    "ଭେଲ୍ଲାଲାର" to R.string.caste_vellalar, // Odia
    "వెల్లాలర్" to R.string.caste_vellalar, // Telugu
    "वेल्लालर" to R.string.caste_vellalar, // Marathi
    "વેલ્લાલર" to R.string.caste_vellalar, // Gujarati
    "ವೆಲ್ಲಾಲರ್" to R.string.caste_vellalar, // Kannada
    "വെള്ളാളർ" to R.string.caste_vellalar, // Malayalam
    "ভেল্লালাৰ" to R.string.caste_vellalar, // Assamese
    "ਵੇਲਾਲਰ" to R.string.caste_vellalar, // Punjabi

    // Naidu
    "Naidu" to R.string.caste_naidu,
    "নায়ডু" to R.string.caste_naidu, // Bengali
    "नायडू" to R.string.caste_naidu, // Hindi
    "நாயுடு" to R.string.caste_naidu, // Tamil
    "ନାୟଡୁ" to R.string.caste_naidu, // Odia
    "నాయుడు" to R.string.caste_naidu, // Telugu
    "नायडू" to R.string.caste_naidu, // Marathi
    "નાયડુ" to R.string.caste_naidu, // Gujarati
    "ನಾಯ್ಡು" to R.string.caste_naidu, // Kannada
    "നായിഡു" to R.string.caste_naidu, // Malayalam
    "নায়ডু" to R.string.caste_naidu, // Assamese
    "ਨਾਇਡੂ" to R.string.caste_naidu, // Punjabi

    // Ezhava
    "Ezhava" to R.string.caste_ezhava,
    "এঝাভা" to R.string.caste_ezhava, // Bengali
    "एझावा" to R.string.caste_ezhava, // Hindi
    "எழவர்" to R.string.caste_ezhava, // Tamil
    "ଏଝାଭା" to R.string.caste_ezhava, // Odia
    "ఎజ్హవ" to R.string.caste_ezhava, // Telugu
    "एझावा" to R.string.caste_ezhava, // Marathi
    "એઝાવા" to R.string.caste_ezhava, // Gujarati
    "ಎಜ್ಹವ" to R.string.caste_ezhava, // Kannada
    "എഴവ" to R.string.caste_ezhava, // Malayalam
    "এঝাৱা" to R.string.caste_ezhava, // Assamese
    "ਏਜ਼ਾਵਾ" to R.string.caste_ezhava, // Punjabi

    // Gowda
    "Gowda" to R.string.caste_gowda,
    "গৌড়া" to R.string.caste_gowda, // Bengali
    "गौड़ा" to R.string.caste_gowda, // Hindi
    "கவுடர்" to R.string.caste_gowda, // Tamil
    "ଗୌଡ଼ା" to R.string.caste_gowda, // Odia
    "గౌడ" to R.string.caste_gowda, // Telugu
    "गौडा" to R.string.caste_gowda, // Marathi
    "ગૌડા" to R.string.caste_gowda, // Gujarati
    "ಗೌಡ" to R.string.caste_gowda, // Kannada
    "ഗൗഡ" to R.string.caste_gowda, // Malayalam
    "গৌড়া" to R.string.caste_gowda, // Assamese
    "ਗੌੜਾ" to R.string.caste_gowda, // Punjabi

    // Patel
    "Patel" to R.string.caste_patel,
    "প্যাটেল" to R.string.caste_patel, // Bengali
    "पटेल" to R.string.caste_patel, // Hindi
    "படேல்" to R.string.caste_patel, // Tamil
    "ପଟେଲ" to R.string.caste_patel, // Odia
    "పటేల్" to R.string.caste_patel, // Telugu
    "पटेल" to R.string.caste_patel, // Marathi
    "પટેલ" to R.string.caste_patel, // Gujarati
    "ಪಟೇಲ್" to R.string.caste_patel, // Kannada
    "പട്ടേൽ" to R.string.caste_patel, // Malayalam
    "পেটেল" to R.string.caste_patel, // Assamese
    "ਪਟੇਲ" to R.string.caste_patel, // Punjabi

    // Maratha
    "Maratha" to R.string.caste_maratha,
    "মারাঠা" to R.string.caste_maratha, // Bengali
    "मराठा" to R.string.caste_maratha, // Hindi
    "மராத்தா" to R.string.caste_maratha, // Tamil
    "ମରାଠା" to R.string.caste_maratha, // Odia
    "మరాఠా" to R.string.caste_maratha, // Telugu
    "मराठा" to R.string.caste_maratha, // Marathi
    "મરાઠા" to R.string.caste_maratha, // Gujarati
    "ಮರಾಠಾ" to R.string.caste_maratha, // Kannada
    "മറാത്ത" to R.string.caste_maratha, // Malayalam
    "মাৰাঠা" to R.string.caste_maratha, // Assamese
    "ਮਰਾਠਾ" to R.string.caste_maratha, // Punjabi

    // Kurmi
    "Kurmi" to R.string.caste_kurmi,
    "কুর্মি" to R.string.caste_kurmi, // Bengali
    "कुर्मी" to R.string.caste_kurmi, // Hindi
    "குர்மி" to R.string.caste_kurmi, // Tamil
    "କୁର୍ମୀ" to R.string.caste_kurmi, // Odia
    "కుర్మి" to R.string.caste_kurmi, // Telugu
    "कुर्मी" to R.string.caste_kurmi, // Marathi
    "કુર્મી" to R.string.caste_kurmi, // Gujarati
    "ಕುರ್ಮಿ" to R.string.caste_kurmi, // Kannada
    "കുർമി" to R.string.caste_kurmi, // Malayalam
    "কুৰ্মি" to R.string.caste_kurmi, // Assamese
    "ਕੁਰਮੀ" to R.string.caste_kurmi, // Punjabi

    // Lingayat
    "Lingayat" to R.string.caste_lingayat,
    "লিঙ্গায়ত" to R.string.caste_lingayat, // Bengali
    "लिंगायत" to R.string.caste_lingayat, // Hindi
    "லிங்காயத்" to R.string.caste_lingayat, // Tamil
    "ଲିଙ୍ଗାୟତ" to R.string.caste_lingayat, // Odia
    "లింగాయత్" to R.string.caste_lingayat, // Telugu
    "लिंगायत" to R.string.caste_lingayat, // Marathi
    "લિંગાયત" to R.string.caste_lingayat, // Gujarati
    "ಲಿಂಗಾಯತ್" to R.string.caste_lingayat, // Kannada
    "ലിംഗായത്ത്" to R.string.caste_lingayat, // Malayalam
    "লিঙ্গায়ত" to R.string.caste_lingayat, // Assamese
    "ਲਿੰਗਾਯਤ" to R.string.caste_lingayat, // Punjabi

    // Reddy
    "Reddy" to R.string.caste_reddy,
    "রেড্ডি" to R.string.caste_reddy, // Bengali
    "रेड्डी" to R.string.caste_reddy, // Hindi
    "ரெட்டி" to R.string.caste_reddy, // Tamil
    "ରେଡ୍ଡୀ" to R.string.caste_reddy, // Odia
    "రెడ్డి" to R.string.caste_reddy, // Telugu
    "रेड्डी" to R.string.caste_reddy, // Marathi
    "રેડ્ડી" to R.string.caste_reddy, // Gujarati
    "ರೆಡ್ಡಿ" to R.string.caste_reddy, // Kannada
    "റെഡ്ഡി" to R.string.caste_reddy, // Malayalam
    "ৰেড্ডি" to R.string.caste_reddy, // Assamese
    "ਰੈਡੀ" to R.string.caste_reddy, // Punjabi

    // Bhil
    "Bhil" to R.string.caste_bhil,
    "ভিল" to R.string.caste_bhil, // Bengali
    "भील" to R.string.caste_bhil, // Hindi
    "பில்" to R.string.caste_bhil, // Tamil
    "ଭିଲ" to R.string.caste_bhil, // Odia
    "భిల్" to R.string.caste_bhil, // Telugu
    "भील" to R.string.caste_bhil, // Marathi
    "ભીલ" to R.string.caste_bhil, // Gujarati
    "ಭಿಲ್" to R.string.caste_bhil, // Kannada
    "ഭിൽ" to R.string.caste_bhil, // Malayalam
    "ভিল" to R.string.caste_bhil, // Assamese
    "ਭੀਲ" to R.string.caste_bhil, // Punjabi

    // Scheduled Caste (SC)
    "Scheduled Caste" to R.string.caste_scheduled_caste,
    "তফসিলি জাতি" to R.string.caste_scheduled_caste, // Bengali
    "अनुसूचित जाति" to R.string.caste_scheduled_caste, // Hindi
    "தாழ்த்தப்பட்ட" to R.string.caste_scheduled_caste, // Tamil
    "ଅନୁସୂଚିତ ଜାତି" to R.string.caste_scheduled_caste, // Odia
    "షెడ్యూల్డ్ కులం" to R.string.caste_scheduled_caste, // Telugu
    "अनुसूचित जाती" to R.string.caste_scheduled_caste, // Marathi
    "અનુસૂચિત જાતિ" to R.string.caste_scheduled_caste, // Gujarati
    "ಪರಿಶಿಷ್ಟ ಜಾತಿ" to R.string.caste_scheduled_caste, // Kannada
    "ഷെഡ്യൂൾഡ് കാസ്റ്റ്" to R.string.caste_scheduled_caste, // Malayalam
    "তফছিলী জাতি" to R.string.caste_scheduled_caste, // Assamese
    "ਅਨੁਸੂਚਿਤ ਜਾਤੀ" to R.string.caste_scheduled_caste, // Punjabi

    // Scheduled Tribe (ST)
    "Scheduled Tribe" to R.string.caste_scheduled_tribe,
    "তফসিলি উপজাতি" to R.string.caste_scheduled_tribe, // Bengali
    "अनुसूचित जनजाति" to R.string.caste_scheduled_tribe, // Hindi
    "பழங்குடி" to R.string.caste_scheduled_tribe, // Tamil
    "ଅନୁସୂଚିତ ଜନଜାତି" to R.string.caste_scheduled_tribe, // Odia
    "షెడ్యూల్డ్ తెగ" to R.string.caste_scheduled_tribe, // Telugu
    "अनुसूचित जमाती" to R.string.caste_scheduled_tribe, // Marathi
    "અનુસૂચિત આદિવાસી" to R.string.caste_scheduled_tribe, // Gujarati
    "ಪರಿಶಿಷ್ಟ ಬುಡಕಟ್ಟು" to R.string.caste_scheduled_tribe, // Kannada
    "ഷെഡ്യൂൾഡ് ട്രൈബ്" to R.string.caste_scheduled_tribe, // Malayalam
    "তফছিলী উপজাতি" to R.string.caste_scheduled_tribe, // Assamese
"ਅਨੁਸੂਚਿਤ ਕਬੀਲੇ" to R.string.caste_scheduled_tribe, // Punjabi

// Other Backward Class (OBC)
"Other Backward Class" to R.string.caste_obc,
"অন্যান্য পশ্চাদপদ শ্রেণী" to R.string.caste_obc, // Bengali
"अन्य पिछड़ा वर्ग" to R.string.caste_obc, // Hindi
"பிற பிற்படுத்தப்பட்ட" to R.string.caste_obc, // Tamil
"ଅନ୍ୟାନ୍ୟ ପଛୁଆ ବର" to R.string.caste_obc, // Odia
"ఇతర వెనుకబడిన తర" to R.string.caste_obc, // Telugu
"इतर मागास" to R.string.caste_obc, // Marathi
"અન્ય પછાત વ" to R.string.caste_obc, // Gujarati
"ಇತರ ಹಿಂದುಳಿದ ವ" to R.string.caste_obc, // Kannada
"മറ്റ് പിന്നോക്ക" to R.string.caste_obc, // Malayalam
"অন্য পিছপৰা শ্ৰেণী" to R.string.caste_obc, // Assamese
"ਹੋਰ ਪਛੜੀਆਂ ਜਾਤੀਆਂ" to R.string.caste_obc, // Punjabi

// General
"General" to R.string.caste_general,
"সাধারণ" to R.string.caste_general, // Bengali
"सामान्य" to R.string.caste_general, // Hindi
"பொது" to R.string.caste_general, // Tamil
"ସାଧାରଣ" to R.string.caste_general, // Odia
"సామాన్య" to R.string.caste_general, // Telugu
"सामान्य" to R.string.caste_general, // Marathi
"સામાન્ય" to R.string.caste_general, // Gujarati
"ಸಾಮಾನ್ಯ" to R.string.caste_general, // Kannada
"ജനറൽ" to R.string.caste_general, // Malayalam
"সাধাৰণ" to R.string.caste_general, // Assamese
"ਜਨਰ" to R.string.caste_general, // Punjabi

// Other
"Other" to R.string.caste_other,
"অন্যান্য" to R.string.caste_other, // Bengali
"अन्य" to R.string.caste_other, // Hindi
"மற்றவை" to R.string.caste_other, // Tamil
"ଅନ୍ୟାନ୍ୟ" to R.string.caste_other, // Odia
"ఇతర" to R.string.caste_other, // Telugu
"इतर" to R.string.caste_other, // Marathi
"અન્ય" to R.string.caste_other, // Gujarati
"ಇತರೆ" to R.string.caste_other, // Kannada
"മറ്റുള്ള" to R.string.caste_other, // Malayalam
"অন্যান্য" to R.string.caste_other, // Assamese
"ਹੋਰ" to R.string.caste_other // Punjabi
)

val genderNameToRes = mapOf(

    "Hombre" to R.string.male_option,
    "Mujer" to R.string.female_option,
    "Otro" to R.string.gender_other,
    "Otra" to R.string.gender_other,
    // (You already have "Otros" → keeping it is fine as a variant)
    "No binario" to R.string.gender_other,
    // Male
    "Male" to R.string.male_option,
    "পুরুষ" to R.string.male_option, // Bengali
    "पुरुष" to R.string.male_option, // Hindi
    "ஆண்" to R.string.male_option, // Tamil
    "ପୁରୁଷ" to R.string.male_option, // Odia
    "పురుషుడు" to R.string.male_option, // Telugu
    "पुरुष" to R.string.male_option, // Marathi
    "પુરુષ" to R.string.male_option, // Gujarati
    "ಗಂಡಸು" to R.string.male_option, // Kannada
    "പുരുഷൻ" to R.string.male_option, // Malayalam
    "পুৰুষ" to R.string.male_option, // Assamese
    "ਮਰਦ" to R.string.male_option, // Punjabi

    // Female
    "Female" to R.string.female_option,
    "মহিলা" to R.string.female_option, // Bengali
    "महिला" to R.string.female_option, // Hindi
    "பெண்" to R.string.female_option, // Tamil
    "ମହିଳା" to R.string.female_option, // Odia
    "స్త్రీ" to R.string.female_option, // Telugu
    "स्त्री" to R.string.female_option, // Marathi
    "સ્ત્રી" to R.string.female_option, // Gujarati
    "ಹೆಣ್ಣು" to R.string.female_option, // Kannada
    "സ്ത്രീ" to R.string.female_option, // Malayalam
    "মহিলা" to R.string.female_option, // Assamese
    "ਔਰਤ" to R.string.female_option, // Punjabi

    // Other
    "Other" to R.string.gender_other,
    "Otros" to R.string.gender_other, // Spanish
    "অন্যান্য" to R.string.gender_other, // Bengali
    "अन्य" to R.string.gender_other, // Hindi
    "மற்றவை" to R.string.gender_other, // Tamil
    "ଅନ୍ୟାନ୍ୟ" to R.string.gender_other, // Odia
    "ఇతర" to R.string.gender_other, // Telugu
    "इतर" to R.string.gender_other, // Marathi
    "અન્ય" to R.string.gender_other, // Gujarati
    "ಇತರೆ" to R.string.gender_other, // Kannada
    "മറ്റുള്ളവ" to R.string.gender_other, // Malayalam
    "অন্যান্য" to R.string.gender_other, // Assamese
    "ਹੋਰ" to R.string.gender_other // Punjabi
)

val communityNameToRes = mapOf(
    // Adi
    "Adi" to R.string.community_adi,
    "আদি" to R.string.community_adi, // Bengali
    "आदि" to R.string.community_adi, // Hindi
    "ஆதி" to R.string.community_adi, // Tamil
    "ଆଦି" to R.string.community_adi, // Odia
    "ఆది" to R.string.community_adi, // Telugu
    "आदी" to R.string.community_adi, // Marathi
    "આદિ" to R.string.community_adi, // Gujarati
    "ಆದಿ" to R.string.community_adi, // Kannada
    "ആദി" to R.string.community_adi, // Malayalam
    "আদি" to R.string.community_adi, // Assamese
    "ਆਦਿ" to R.string.community_adi, // Punjabi

    // Nyishi
    "Nyishi" to R.string.community_nyishi,
    "ন্যিশি" to R.string.community_nyishi, // Bengali
    "न्यिशी" to R.string.community_nyishi, // Hindi
    "நியிஷி" to R.string.community_nyishi, // Tamil
    "ନ୍ୟିଶି" to R.string.community_nyishi, // Odia
    "న్యిషి" to R.string.community_nyishi, // Telugu
    "न्यिशी" to R.string.community_nyishi, // Marathi
    "ન્યિશી" to R.string.community_nyishi, // Gujarati
    "ನ್ಯಿಷಿ" to R.string.community_nyishi, // Kannada
    "ന്യിഷി" to R.string.community_nyishi, // Malayalam
    "ন্যিশি" to R.string.community_nyishi, // Assamese
    "ਨਿਯਸ਼ੀ" to R.string.community_nyishi, // Punjabi

    // Bodo
    "Bodo" to R.string.community_bodo,
    "বড়ো" to R.string.community_bodo, // Bengali
    "बोडो" to R.string.community_bodo, // Hindi
    "போடோ" to R.string.community_bodo, // Tamil
    "ବଡ଼ୋ" to R.string.community_bodo, // Odia
    "బోడో" to R.string.community_bodo, // Telugu
    "बोडो" to R.string.community_bodo, // Marathi
    "બોડો" to R.string.community_bodo, // Gujarati
    "ಬೋಡೋ" to R.string.community_bodo, // Kannada
    "ബോഡോ" to R.string.community_bodo, // Malayalam
    "বড়ো" to R.string.community_bodo, // Assamese
    "ਬੋਡੋ" to R.string.community_bodo, // Punjabi

    // Assamese
    "Assamese" to R.string.community_assamese,
    "অসমীয়া" to R.string.community_assamese, // Bengali
    "असमिया" to R.string.community_assamese, // Hindi
    "அஸ்ஸாமி" to R.string.community_assamese, // Tamil
    "ଅସମୀୟା" to R.string.community_assamese, // Odia
    "అస్సామీ" to R.string.community_assamese, // Telugu
    "आसामी" to R.string.community_assamese, // Marathi
    "આસામી" to R.string.community_assamese, // Gujarati
    "ಅಸ್ಸಾಮಿ" to R.string.community_assamese, // Kannada
    "അസമീസ്" to R.string.community_assamese, // Malayalam
    "অসমীয়া" to R.string.community_assamese, // Assamese
    "ਅਸਮੀ" to R.string.community_assamese, // Punjabi

    // Manipuri
    "Manipuri" to R.string.community_manipuri,
    "মণিপুরী" to R.string.community_manipuri, // Bengali
    "मणिपुरी" to R.string.community_manipuri, // Hindi
    "மணிப்பூரி" to R.string.community_manipuri, // Tamil
    "ମଣିପୁରୀ" to R.string.community_manipuri, // Odia
    "మణిపురి" to R.string.community_manipuri, // Telugu
    "मणिपुरी" to R.string.community_manipuri, // Marathi
    "મણિપુરી" to R.string.community_manipuri, // Gujarati
    "ಮಣಿಪುರಿ" to R.string.community_manipuri, // Kannada
    "മണിപുരി" to R.string.community_manipuri, // Malayalam
    "মণিপুৰী" to R.string.community_manipuri, // Assamese
    "ਮਣੀਪੁਰੀ" to R.string.community_manipuri, // Punjabi

    // Khasi
    "Khasi" to R.string.community_khasi,
    "খাসি" to R.string.community_khasi, // Bengali
    "खासी" to R.string.community_khasi, // Hindi
    "காசி" to R.string.community_khasi, // Tamil
    "ଖାସି" to R.string.community_khasi, // Odia
    "ఖాసి" to R.string.community_khasi, // Telugu
    "खासी" to R.string.community_khasi, // Marathi
    "ખાસી" to R.string.community_khasi, // Gujarati
    "ಖಾಸಿ" to R.string.community_khasi, // Kannada
    "ഖാസി" to R.string.community_khasi, // Malayalam
    "খাচি" to R.string.community_khasi, // Assamese
    "ਖਾਸੀ" to R.string.community_khasi, // Punjabi

    // Mizo
    "Mizo" to R.string.community_mizo,
    "মিজো" to R.string.community_mizo, // Bengali
    "मिज़ो" to R.string.community_mizo, // Hindi
    "மிசோ" to R.string.community_mizo, // Tamil
    "ମିଜୋ" to R.string.community_mizo, // Odia
    "మిజో" to R.string.community_mizo, // Telugu
    "मिझो" to R.string.community_mizo, // Marathi
    "મિઝો" to R.string.community_mizo, // Gujarati
    "ಮಿಜೋ" to R.string.community_mizo, // Kannada
    "മിസോ" to R.string.community_mizo, // Malayalam
    "মিজো" to R.string.community_mizo, // Assamese
    "ਮਿਜ਼ੋ" to R.string.community_mizo, // Punjabi

    // Naga
    "Naga" to R.string.community_naga,
    "নাগা" to R.string.community_naga, // Bengali
    "नागा" to R.string.community_naga, // Hindi
    "நாகா" to R.string.community_naga, // Tamil
    "ନାଗା" to R.string.community_naga, // Odia
    "నాగా" to R.string.community_naga, // Telugu
    "नागा" to R.string.community_naga, // Marathi
    "નાગા" to R.string.community_naga, // Gujarati
    "ನಾಗಾ" to R.string.community_naga, // Kannada
    "നാഗ" to R.string.community_naga, // Malayalam
    "নাগা" to R.string.community_naga, // Assamese
    "ਨਾਗਾ" to R.string.community_naga, // Punjabi

    // Tripuri
    "Tripuri" to R.string.community_tripuri,
    "ত্রিপুরী" to R.string.community_tripuri, // Bengali
    "त्रिपुरी" to R.string.community_tripuri, // Hindi
    "திரிபுரி" to R.string.community_tripuri, // Tamil
    "ତ୍ରିପୁରୀ" to R.string.community_tripuri, // Odia
    "త్రిపురి" to R.string.community_tripuri, // Telugu
    "त्रिपुरी" to R.string.community_tripuri, // Marathi
    "ત્રિપુરી" to R.string.community_tripuri, // Gujarati
    "ತ್ರಿಪುರಿ" to R.string.community_tripuri, // Kannada
    "ത്രിപുരി" to R.string.community_tripuri, // Malayalam
    "ত্ৰিপুৰী" to R.string.community_tripuri, // Assamese
    "ਤ੍ਰਿਪੁਰੀ" to R.string.community_tripuri, // Punjabi

    // Bengali
    "Bengali" to R.string.community_bengali,
    "বাঙালি" to R.string.community_bengali, // Bengali
    "बंगाली" to R.string.community_bengali, // Hindi
    "பெங்காலி" to R.string.community_bengali, // Tamil
    "ବଙ୍ଗାଳୀ" to R.string.community_bengali, // Odia
    "బెంగాలీ" to R.string.community_bengali, // Telugu
    "बंगाली" to R.string.community_bengali, // Marathi
    "બંગાળી" to R.string.community_bengali, // Gujarati
    "ಬೆಂಗಾಲಿ" to R.string.community_bengali, // Kannada
    "ബംഗാളി" to R.string.community_bengali, // Malayalam
    "বঙালী" to R.string.community_bengali, // Assamese
    "ਬੰਗਾਲੀ" to R.string.community_bengali, // Punjabi

    // Santhal
    "Santhal" to R.string.community_santhal,
    "সাঁওতাল" to R.string.community_santhal, // Bengali
    "संताल" to R.string.community_santhal, // Hindi
    "சந்தால்" to R.string.community_santhal, // Tamil
    "ସାନ୍ଥାଳ" to R.string.community_santhal, // Odia
    "సంతాల్" to R.string.community_santhal, // Telugu
    "संताल" to R.string.community_santhal, // Marathi
    "સંતાલ" to R.string.community_santhal, // Gujarati
    "ಸಂತಾಲ್" to R.string.community_santhal, // Kannada
    "സന്താൾ" to R.string.community_santhal, // Malayalam
    "চাঁওতাল" to R.string.community_santhal, // Assamese
    "ਸੰਥਾਲ" to R.string.community_santhal, // Punjabi

    // Oraon
    "Oraon" to R.string.community_oraon,
    "ওরাওঁ" to R.string.community_oraon, // Bengali
    "उरांव" to R.string.community_oraon, // Hindi
    "ஒரான்" to R.string.community_oraon, // Tamil
    "ଓରାଓଁ" to R.string.community_oraon, // Odia
    "ఒరావ్" to R.string.community_oraon, // Telugu
    "उरांव" to R.string.community_oraon, // Marathi
    "ઓરાંવ" to R.string.community_oraon, // Gujarati
    "ಒರಾಂವ್" to R.string.community_oraon, // Kannada
    "ഒറാവോൺ" to R.string.community_oraon, // Malayalam
    "ওৰাঁও" to R.string.community_oraon, // Assamese
    "ਉਰਾਂਵ" to R.string.community_oraon, // Punjabi

    // Munda
    "Munda" to R.string.community_munda,
    "মুন্ডা" to R.string.community_munda, // Bengali
    "मुंडा" to R.string.community_munda, // Hindi
    "முண்டா" to R.string.community_munda, // Tamil
    "ମୁଣ୍ଡା" to R.string.community_munda, // Odia
    "ముండా" to R.string.community_munda, // Telugu
    "मुंडा" to R.string.community_munda, // Marathi
    "મુંડા" to R.string.community_munda, // Gujarati
    "ಮುಂಡಾ" to R.string.community_munda, // Kannada
    "മുണ്ട" to R.string.community_munda, // Malayalam
    "মুণ্ডা" to R.string.community_munda, // Assamese
    "ਮੁੰਡਾ" to R.string.community_munda, // Punjabi

    // Punjabi
    "Punjabi" to R.string.community_punjabi,
    "পাঞ্জাবি" to R.string.community_punjabi, // Bengali
    "पंजाबी" to R.string.community_punjabi, // Hindi
    "பஞ்சாபி" to R.string.community_punjabi, // Tamil
    "ପଞ୍ଜାବୀ" to R.string.community_punjabi, // Odia
    "పంజాబీ" to R.string.community_punjabi, // Telugu
    "पंजाबी" to R.string.community_punjabi, // Marathi
    "પંજાબી" to R.string.community_punjabi, // Gujarati
    "ಪಂಜಾಬಿ" to R.string.community_punjabi, // Kannada
    "പഞ്ചാബി" to R.string.community_punjabi, // Malayalam
    "পাঞ্জাবী" to R.string.community_punjabi, // Assamese
    "ਪੰਜਾਬੀ" to R.string.community_punjabi, // Punjabi

    // Tamil
    "Tamil" to R.string.community_tamil,
    "তামিল" to R.string.community_tamil, // Bengali
    "तमिल" to R.string.community_tamil, // Hindi
    "தமிழ்" to R.string.community_tamil, // Tamil
    "ତାମିଲ" to R.string.community_tamil, // Odia
    "తమిళ" to R.string.community_tamil, // Telugu
    "तामिळ" to R.string.community_tamil, // Marathi
    "તમિલ" to R.string.community_tamil, // Gujarati
    "ತಮಿಳು" to R.string.community_tamil, // Kannada
    "തമിഴ്" to R.string.community_tamil, // Malayalam
    "তামিল" to R.string.community_tamil, // Assamese
    "ਤਮਿਲ" to R.string.community_tamil, // Punjabi

    // Telugu
    "Telugu" to R.string.community_telugu,
    "তেলুগু" to R.string.community_telugu, // Bengali
    "तेलुगु" to R.string.community_telugu, // Hindi
    "தெலுங்கு" to R.string.community_telugu, // Tamil
    "ତେଲୁଗୁ" to R.string.community_telugu, // Odia
    "తెలుగు" to R.string.community_telugu, // Telugu
    "तेलुगु" to R.string.community_telugu, // Marathi
    "તેલુગુ" to R.string.community_telugu, // Gujarati
    "ತೆಲುಗು" to R.string.community_telugu, // Kannada
    "തെലുഗു" to R.string.community_telugu, // Malayalam
    "তেলুগু" to R.string.community_telugu, // Assamese
    "ਤੇਲਗੂ" to R.string.community_telugu, // Punjabi

    // Kannadiga
    "Kannadiga" to R.string.community_kannadiga,
    "কন্নড়িগা" to R.string.community_kannadiga, // Bengali
    "कन्नडिगा" to R.string.community_kannadiga, // Hindi
    "கன்னடிகா" to R.string.community_kannadiga, // Tamil
    "କନ୍ନଡ଼ିଗା" to R.string.community_kannadiga, // Odia
    "కన్నడిగా" to R.string.community_kannadiga, // Telugu
    "कन्नडिगा" to R.string.community_kannadiga, // Marathi
    "કન્નડિગા" to R.string.community_kannadiga, // Gujarati
    "ಕನ್ನಡಿಗ" to R.string.community_kannadiga, // Kannada
    "കന്നഡിഗ" to R.string.community_kannadiga, // Malayalam
    "কন্নড়িগা" to R.string.community_kannadiga, // Assamese
    "ਕੰਨੜੀਗਾ" to R.string.community_kannadiga, // Punjabi

    // Malayali
    "Malayali" to R.string.community_malayali,
    "মালয়ালি" to R.string.community_malayali, // Bengali
    "मलयाली" to R.string.community_malayali, // Hindi
    "மலையாளி" to R.string.community_malayali, // Tamil
    "ମାଲୟାଳୀ" to R.string.community_malayali, // Odia
    "మలయాళి" to R.string.community_malayali, // Telugu
    "मल्याळी" to R.string.community_malayali, // Marathi
    "મલયાળી" to R.string.community_malayali, // Gujarati
    "ಮಲಯಾಳಿ" to R.string.community_malayali, // Kannada
    "മലയാളി" to R.string.community_malayali, // Malayalam
    "মালয়ালী" to R.string.community_malayali, // Assamese
    "ਮਲਯਾਲੀ" to R.string.community_malayali, // Punjabi

    // Gujarati
    "Gujarati" to R.string.community_gujarati,
    "গুজরাটি" to R.string.community_gujarati, // Bengali
    "गुजराती" to R.string.community_gujarati, // Hindi
    "குஜராத்தி" to R.string.community_gujarati, // Tamil
    "ଗୁଜରାଟୀ" to R.string.community_gujarati, // Odia
    "గుజరాతీ" to R.string.community_gujarati, // Telugu
    "गुजराती" to R.string.community_gujarati, // Marathi
    "ગુજરાતી" to R.string.community_gujarati, // Gujarati
    "ಗುಜರಾತಿ" to R.string.community_gujarati, // Kannada
    "ഗുജറാത്തി" to R.string.community_gujarati, // Malayalam
    "গুজৰাটী" to R.string.community_gujarati, // Assamese
    "ਗੁਜਰਾਤੀ" to R.string.community_gujarati, // Punjabi

    // Marathi
    "Marathi" to R.string.community_marathi,
    "মারাঠি" to R.string.community_marathi, // Bengali
    "मराठी" to R.string.community_marathi, // Hindi
    "மராத்தி" to R.string.community_marathi, // Tamil
    "ମରାଠୀ" to R.string.community_marathi, // Odia
    "మరాఠీ" to R.string.community_marathi, // Telugu
    "मराठी" to R.string.community_marathi, // Marathi
    "મરાઠી" to R.string.community_marathi, // Gujarati
    "ಮರಾಠಿ" to R.string.community_marathi, // Kannada
    "മറാത്തി" to R.string.community_marathi, // Malayalam
    "মাৰাঠী" to R.string.community_marathi, // Assamese
    "ਮਰਾਠੀ" to R.string.community_marathi, // Punjabi

    // Odia
    "Odia" to R.string.community_odia,
    "ওড়িয়া" to R.string.community_odia, // Bengali
    "उड़िया" to R.string.community_odia, // Hindi
    "ஒடியா" to R.string.community_odia, // Tamil
    "ଓଡ଼ିଆ" to R.string.community_odia, // Odia
    "ఒడియా" to R.string.community_odia, // Telugu
    "ओडिया" to R.string.community_odia, // Marathi
    "ઓડિયા" to R.string.community_odia, // Gujarati
    "ಒಡಿಯಾ" to R.string.community_odia, // Kannada
    "ഒഡിയ" to R.string.community_odia, // Malayalam
    "ওড়িয়া" to R.string.community_odia, // Assamese
    "ਓਡੀਆ" to R.string.community_odia, // Punjabi

    // Bihari
    "Bihari" to R.string.community_bihari,
    "বিহারী" to R.string.community_bihari, // Bengali
    "बिहारी" to R.string.community_bihari, // Hindi
    "பிஹாரி" to R.string.community_bihari, // Tamil
    "ବିହାରୀ" to R.string.community_bihari, // Odia
    "బిహారీ" to R.string.community_bihari, // Telugu
    "बिहारी" to R.string.community_bihari, // Marathi
    "બિહારી" to R.string.community_bihari, // Gujarati
    "ಬಿಹಾರಿ" to R.string.community_bihari, // Kannada
    "ബിഹാരി" to R.string.community_bihari, // Malayalam
    "বিহাৰী" to R.string.community_bihari, // Assamese
    "ਬਿਹਾਰੀ" to R.string.community_bihari, // Punjabi

    // Marwari
    "Marwari" to R.string.community_marwari,
    "মারোয়াড়ি" to R.string.community_marwari, // Bengali
    "मारवाड़ी" to R.string.community_marwari, // Hindi
    "மார்வாரி" to R.string.community_marwari, // Tamil
    "ମାରୱାଡ଼ି" to R.string.community_marwari, // Odia
    "మార్వాడీ" to R.string.community_marwari, // Telugu
    "मारवाडी" to R.string.community_marwari, // Marathi
    "મારવાડી" to R.string.community_marwari, // Gujarati
    "ಮಾರ್ವಾಡಿ" to R.string.community_marwari, // Kannada
    "മാർവാഡി" to R.string.community_marwari, // Malayalam
    "মাৰৱাৰী" to R.string.community_marwari, // Assamese
    "ਮਾਰਵਾੜੀ" to R.string.community_marwari, // Punjabi

    // Rajasthani
    "Rajasthani" to R.string.community_rajasthani,
    "রাজস্থানী" to R.string.community_rajasthani, // Bengali
    "राजस्थानी" to R.string.community_rajasthani, // Hindi
    "ராஜஸ்தானி" to R.string.community_rajasthani, // Tamil
    "ରାଜସ୍ଥାନୀ" to R.string.community_rajasthani, // Odia
    "రాజస్థానీ" to R.string.community_rajasthani, // Telugu
    "राजस्थानी" to R.string.community_rajasthani, // Marathi
    "રાજસ્થાની" to R.string.community_rajasthani, // Gujarati
    "ರಾಜಸ್ಥಾನಿ" to R.string.community_rajasthani, // Kannada
    "രാജസ്ഥാനി" to R.string.community_rajasthani, // Malayalam
    "ৰাজস্থানী" to R.string.community_rajasthani, // Assamese
    "ਰਾਜਸਥਾਨੀ" to R.string.community_rajasthani, // Punjabi

    // Kashmiri
    "Kashmiri" to R.string.community_kashmiri,
    "কাশ্মীরি" to R.string.community_kashmiri, // Bengali
    "कश्मीरी" to R.string.community_kashmiri, // Hindi
    "காஷ்மீரி" to R.string.community_kashmiri, // Tamil
    "କାଶ୍ମୀରୀ" to R.string.community_kashmiri, // Odia
    "కాశ్మీరీ" to R.string.community_kashmiri, // Telugu
    "काश्मिरी" to R.string.community_kashmiri, // Marathi
    "કાશ્મીરી" to R.string.community_kashmiri, // Gujarati
    "ಕಾಶ್ಮೀರಿ" to R.string.community_kashmiri, // Kannada
    "കശ്മീരി" to R.string.community_kashmiri, // Malayalam
    "কাশ্মীৰী" to R.string.community_kashmiri, // Assamese
    "ਕਸ਼ਮੀਰੀ" to R.string.community_kashmiri, // Punjabi

    // Himachali
    "Himachali" to R.string.community_himachali,
    "হিমাচলি" to R.string.community_himachali, // Bengali
    "हिमाचली" to R.string.community_himachali, // Hindi
    "ஹிமாச்சலி" to R.string.community_himachali, // Tamil
    "ହିମାଚଳୀ" to R.string.community_himachali, // Odia
    "హిమాచలీ" to R.string.community_himachali, // Telugu
    "हिमाचली" to R.string.community_himachali, // Marathi
    "હિમાચલી" to R.string.community_himachali, // Gujarati
    "ಹಿಮಾಚಲಿ" to R.string.community_himachali, // Kannada
    "ഹിമാചലി" to R.string.community_himachali, // Malayalam
    "হিমাচলী" to R.string.community_himachali, // Assamese
    "ਹਿਮਾਚਲੀ" to R.string.community_himachali, // Punjabi

    // Haryanvi
    "Haryanvi" to R.string.community_haryanvi,
    "হরিয়ানভি" to R.string.community_haryanvi, // Bengali
    "हरियाणवी" to R.string.community_haryanvi, // Hindi
    "ஹரியானவி" to R.string.community_haryanvi, // Tamil
    "ହରିୟାଣଭି" to R.string.community_haryanvi, // Odia
    "హర్యాన్వీ" to R.string.community_haryanvi, // Telugu
    "हरियाणवी" to R.string.community_haryanvi, // Marathi
    "હરિયાણવી" to R.string.community_haryanvi, // Gujarati
    "ಹರಿಯಾಣವಿ" to R.string.community_haryanvi, // Kannada
    "ഹരിയാണവി" to R.string.community_haryanvi, // Malayalam
    "হাৰিয়ানভি" to R.string.community_haryanvi, // Assamese
    "ਹਰਿਆਣਵੀ" to R.string.community_haryanvi, // Punjabi

    // Garhwali
    "Garhwali" to R.string.community_garhwali,
    "গড়ওয়ালি" to R.string.community_garhwali, // Bengali
    "गढ़वाली" to R.string.community_garhwali, // Hindi
    "கார்வாலி" to R.string.community_garhwali, // Tamil
    "ଗଡ଼ୱାଳୀ" to R.string.community_garhwali, // Odia
    "గర్వాలీ" to R.string.community_garhwali, // Telugu
    "गढवाली" to R.string.community_garhwali, // Marathi
    "ગઢવાળી" to R.string.community_garhwali, // Gujarati
    "ಗರ್ವಾಲಿ" to R.string.community_garhwali, // Kannada
    "ഗാർവാലി" to R.string.community_garhwali, // Malayalam
    "গড়ৱালী" to R.string.community_garhwali, // Assamese
    "ਗੜ੍ਹਵਾਲੀ" to R.string.community_garhwali, // Punjabi

    // Madhya Pradeshi
    "Madhya Pradeshi" to R.string.community_madhya_pradeshi,
    "মধ্যপ্রদেশী" to R.string.community_madhya_pradeshi, // Bengali
    "मध्यप्रदेशी" to R.string.community_madhya_pradeshi, // Hindi
    "மத்திய பிரதேசி" to R.string.community_madhya_pradeshi, // Tamil
    "ମଧ୍ୟପ୍ରଦେଶୀ" to R.string.community_madhya_pradeshi, // Odia
    "మధ్యప్రదేశీ" to R.string.community_madhya_pradeshi, // Telugu
    "मध्यप्रदेशी" to R.string.community_madhya_pradeshi, // Marathi
    "મધ્યપ્રદેશી" to R.string.community_madhya_pradeshi, // Gujarati
    "ಮಧ್ಯಪ್ರದೇಶಿ" to R.string.community_madhya_pradeshi, // Kannada
    "മധ്യപ്രദേശി" to R.string.community_madhya_pradeshi, // Malayalam
    "মধ্যপ্ৰদেশী" to R.string.community_madhya_pradeshi, // Assamese
    "ਮੱਧ ਪ੍ਰਦੇਸ਼ੀ" to R.string.community_madhya_pradeshi, // Punjabi

    // Chhattisgarhi
    "Chhattisgarhi" to R.string.community_chhattisgarhi,
    "ছত্তিশগড়ি" to R.string.community_chhattisgarhi, // Bengali
    "छत्तीसगढ़ी" to R.string.community_chhattisgarhi, // Hindi
    "சத்தீஸ்கரி" to R.string.community_chhattisgarhi, // Tamil
    "ଛତିଶଗଡ଼ି" to R.string.community_chhattisgarhi, // Odia
    "ఛత్తీస్‌గఢీ" to R.string.community_chhattisgarhi, // Telugu
    "छत्तीसगढी" to R.string.community_chhattisgarhi, // Marathi
    "છત્તીસગઢી" to R.string.community_chhattisgarhi, // Gujarati
    "ಛತ್ತೀಸ್‌ಗಢಿ" to R.string.community_chhattisgarhi, // Kannada
    "ഛത്തീസ്ഗഢി" to R.string.community_chhattisgarhi, // Malayalam
    "ছত্তীশগড়ি" to R.string.community_chhattisgarhi, // Assamese
    "ਛੱਤੀਸਗੜ੍ਹੀ" to R.string.community_chhattisgarhi, // Punjabi

    // Goan
    "Goan" to R.string.community_goan,
    "গোয়ান" to R.string.community_goan, // Bengali
    "गोवानी" to R.string.community_goan, // Hindi
    "கோவான்" to R.string.community_goan, // Tamil
    "ଗୋଆନ" to R.string.community_goan, // Odia
    "గోవాన్" to R.string.community_goan, // Telugu
    "गोवेकर" to R.string.community_goan, // Marathi
    "ગોવાન" to R.string.community_goan, // Gujarati
    "ಗೋವಾನ್" to R.string.community_goan, // Kannada
    "ഗോവൻ" to R.string.community_goan, // Malayalam
    "গোৱান" to R.string.community_goan, // Assamese
    "ਗੋਆਨ" to R.string.community_goan, // Punjabi

    // Konkani
    "Konkani" to R.string.community_konkani,
    "কোঙ্কণি" to R.string.community_konkani, // Bengali
    "कोंकणी" to R.string.community_konkani, // Hindi
    "கொங்கணி" to R.string.community_konkani, // Tamil
    "କୋଙ୍କଣୀ" to R.string.community_konkani, // Odia
    "కొంకణి" to R.string.community_konkani, // Telugu
    "कोंकणी" to R.string.community_konkani, // Marathi
    "કોંકણી" to R.string.community_konkani, // Gujarati
    "ಕೊಂಕಣಿ" to R.string.community_konkani, // Kannada
    "കൊങ്കണി" to R.string.community_konkani, // Malayalam
    "কোংকণী" to R.string.community_konkani, // Assamese
    "ਕੋਂਕਣੀ" to R.string.community_konkani, // Punjabi

    // Sikkimese
    "Sikkimese" to R.string.community_sikkimese,
    "সিকিমি" to R.string.community_sikkimese, // Bengali
    "सिक्किमी" to R.string.community_sikkimese, // Hindi
    "சிக்கிமி" to R.string.community_sikkimese, // Tamil
    "ସିକ୍କିମୀ" to R.string.community_sikkimese, // Odia
    "సిక్కిమీ" to R.string.community_sikkimese, // Telugu
    "सिक्कीमी" to R.string.community_sikkimese, // Marathi
    "સિક્કીમી" to R.string.community_sikkimese, // Gujarati
    "ಸಿಕ್ಕಿಮಿ" to R.string.community_sikkimese, // Kannada
    "സിക്കിമി" to R.string.community_sikkimese, // Malayalam
    "চিক্কিমী" to R.string.community_sikkimese, // Assamese
    "ਸਿੱਕੀਮੀ" to R.string.community_sikkimese, // Punjabi

    // Nepali
    "Nepali" to R.string.community_nepali,
    "নেপালি" to R.string.community_nepali, // Bengali
    "नेपाली" to R.string.community_nepali, // Hindi
    "நேபாளி" to R.string.community_nepali, // Tamil
    "ନେପାଳୀ" to R.string.community_nepali, // Odia
    "నేపాళీ" to R.string.community_nepali, // Telugu
    "नेपाळी" to R.string.community_nepali, // Marathi
    "નેપાળી" to R.string.community_nepali, // Gujarati
    "ನೇಪಾಳಿ" to R.string.community_nepali, // Kannada
    "നേപ്പാളി" to R.string.community_nepali, // Malayalam
    "নেপালী" to R.string.community_nepali, // Assamese
    "ਨੇਪਾਲੀ" to R.string.community_nepali, // Punjabi

    // Ladakhi
    "Ladakhi" to R.string.community_ladakhi,
    "লাদাখি" to R.string.community_ladakhi, // Bengali
    "लद्दाखी" to R.string.community_ladakhi, // Hindi
    "லடாக்கி" to R.string.community_ladakhi, // Tamil
    "ଲଦାଖୀ" to R.string.community_ladakhi, // Odia
    "లడఖీ" to R.string.community_ladakhi, // Telugu
    "लडाखी" to R.string.community_ladakhi, // Marathi
    "લદ્દાખી" to R.string.community_ladakhi, // Gujarati
    "ಲಡಾಖಿ" to R.string.community_ladakhi, // Kannada
    "ലഡാഖി" to R.string.community_ladakhi, // Malayalam
    "লাদাখি" to R.string.community_ladakhi, // Assamese
    "ਲੱਦਾਖੀ" to R.string.community_ladakhi, // Punjabi

    // Andamanese
    "Andamanese" to R.string.community_andamanese,
    "আন্দামানী" to R.string.community_andamanese, // Bengali
    "अंडमानी" to R.string.community_andamanese, // Hindi
    "அந்தமானி" to R.string.community_andamanese, // Tamil
    "ଆଣ୍ଡାମାନୀ" to R.string.community_andamanese, // Odia
    "అండమానీ" to R.string.community_andamanese, // Telugu
    "अंदमानी" to R.string.community_andamanese, // Marathi
    "અંદામાની" to R.string.community_andamanese, // Gujarati
    "ಅಂಡಮಾನಿ" to R.string.community_andamanese, // Kannada
    "അന്തമാനി" to R.string.community_andamanese, // Malayalam
    "আন্দামানী" to R.string.community_andamanese, // Assamese
    "ਅੰਡਮਾਨੀ" to R.string.community_andamanese, // Punjabi

    // Lakhadweepi
    "Lakhadweepi" to R.string.community_lakhadweepi,
    "লাক্ষাদ্বীপি" to R.string.community_lakhadweepi, // Bengali
    "लक्षद्वीपी" to R.string.community_lakhadweepi, // Hindi
    "லட்சத்தீவி" to R.string.community_lakhadweepi, // Tamil
    "ଲକ୍ଷଦ୍ୱୀପୀ" to R.string.community_lakhadweepi, // Odia
    "లక్షద్వీపి" to R.string.community_lakhadweepi, // Telugu
    "लक्षद्वीपी" to R.string.community_lakhadweepi, // Marathi
    "લક્ષદ્વીપી" to R.string.community_lakhadweepi, // Gujarati
    "ಲಕ್ಷದ್ವೀಪಿ" to R.string.community_lakhadweepi, // Kannada
    "ലക്ഷദ്വീപി" to R.string.community_lakhadweepi, // Malayalam
    "লক্ষদ্বীপি" to R.string.community_lakhadweepi, // Assamese
    "ਲਕਸ਼ਦੀਪੀ" to R.string.community_lakhadweepi, // Punjabi

    // Other
    "Other" to R.string.community_other,
    "অন্যান্য" to R.string.community_other, // Bengali
    "अन्य" to R.string.community_other, // Hindi
    "மற்றவை" to R.string.community_other, // Tamil
    "ଅନ୍ୟାନ୍ୟ" to R.string.community_other, // Odia
    "ఇతర" to R.string.community_other, // Telugu
    "इतर" to R.string.community_other, // Marathi
    "અન્ય" to R.string.community_other, // Gujarati
    "ಇತರೆ" to R.string.community_other, // Kannada
    "മറ്റുള്ളവ" to R.string.community_other, // Malayalam
    "অন্যান্য" to R.string.community_other, // Assamese
    "ਹੋਰ" to R.string.community_other // Punjabi
)

val religionNameToRes = mapOf(
    // Christian denominations
    "Católico" to R.string.religion_christian_catholic,
    "Christian Catholic" to R.string.religion_christian_catholic,
    "Protestante histórico" to R.string.religion_christian_protestant_mainline,
    "Mainline Protestant" to R.string.religion_christian_protestant_mainline,
    "Protestante evangélico" to R.string.religion_christian_evangelical,
    "Evangelical Protestant" to R.string.religion_christian_evangelical,
    "Ortodoxo oriental" to R.string.religion_christian_orthodox,
    "Eastern Orthodox" to R.string.religion_christian_orthodox,
    "Santo de los Últimos Días (Mormón)" to R.string.religion_christian_latter_day_saint,
    "Latter-day Saint (Mormon)" to R.string.religion_christian_latter_day_saint,
    "Testigo de Jehová" to R.string.religion_christian_jehovahs_witness,
    "Jehovah’s Witness" to R.string.religion_christian_jehovahs_witness,
    "Otro cristiano" to R.string.religion_christian_other,
    "Other Christian" to R.string.religion_christian_other,
    // Muslim denominations
    "Suní" to R.string.religion_muslim_sunni,
    "Sunni" to R.string.religion_muslim_sunni,
    "Chií" to R.string.religion_muslim_shia,
    "Shia" to R.string.religion_muslim_shia,
    "Ahmadiyya" to R.string.religion_muslim_ahmadiyya,
    "Sufí" to R.string.religion_muslim_sufi,
    "Sufi" to R.string.religion_muslim_sufi,
    "Otro musulmán" to R.string.religion_muslim_other,
    "Other Muslim" to R.string.religion_muslim_other,
    // Additional religions
    "Sin religión / Laico" to R.string.religion_no_religion,
    "No Religion / Secular" to R.string.religion_no_religion,
    "Zoroastrismo / Parsí" to R.string.religion_parsi,
    "Zoroastrian / Parsi" to R.string.religion_parsi,
    "Indígena / Tribal" to R.string.religion_indigenous_tribal,
    "Indigenous / Tribal" to R.string.religion_indigenous_tribal,
    "Santería (afrocubana)" to R.string.religion_santeria,
    "Santería (Afro-Cuban)" to R.string.religion_santeria,
    "Vudú (haitiano)" to R.string.religion_voodou,
    "Vodou (Haitian)" to R.string.religion_voodou,
    "Candomblé (afrobrasileño)" to R.string.religion_candomble,
    "Candomblé (Afro-Brazilian)" to R.string.religion_candomble,
    "Umbanda (sincrética brasileña)" to R.string.religion_umbanda,
    "Umbanda (Brazilian Syncretic)" to R.string.religion_umbanda,
    "Palo Mayombe" to R.string.religion_palo_mayombe,
    "Tradición nativa americana" to R.string.religion_native_traditional,
    "Native American Traditional" to R.string.religion_native_traditional,
    "Iglesia Nativa Americana (peyotismo)" to R.string.religion_native_church,
    "Native American Church (Peyotism)" to R.string.religion_native_church,
    "Búsqueda de visión / ceremonial" to R.string.religion_vision_quest,
    "Vision Quest / Ceremonial" to R.string.religion_vision_quest,
    "Religión tradicional africana" to R.string.religion_african_traditional,
    "African Traditional Religion" to R.string.religion_african_traditional,
    "Obeah (folclore caribeño)" to R.string.religion_obeah,
    "Obeah (Caribbean Folk)" to R.string.religion_obeah,
    "Hoodoo (folclore afroamericano)" to R.string.religion_hoodoo,
    "Hoodoo (African-American Folk)" to R.string.religion_hoodoo,
    "Rastafarismo" to R.string.religion_rastafari,
    "Rastafarianism" to R.string.religion_rastafari,
    "Tradición protestante negra (p. ej., AME, COGIC)" to R.string.religion_black_protestant,
    "Black Protestant Tradition (e.g. AME, COGIC)" to R.string.religion_black_protestant,
    "Hindú" to R.string.religion_hindu,
    "Musulmán" to R.string.religion_muslim,
    "Cristiano" to R.string.religion_christian,
    "Budista" to R.string.religion_buddhist,
    "Jainista" to R.string.religion_jain,
    "Sij" to R.string.religion_sikh,          // también se usa “Sikh”
    "Judío" to R.string.religion_jewish,
    "Indígena/Tribal" to R.string.religion_indigenous_tribal,
    "Sin religión" to R.string.religion_no_religion,
    "Zoroastriano" to R.string.religion_parsi,
    "Parsi" to R.string.religion_parsi,       // alias común
    "Otro" to R.string.religion_other,
    // Hindu
    "Hindu" to R.string.religion_hindu,
    "হিন্দু" to R.string.religion_hindu, // Bengali
    "हिंदू" to R.string.religion_hindu, // Hindi
    "இந்து" to R.string.religion_hindu, // Tamil
    "ହିନ୍ଦୁ" to R.string.religion_hindu, // Odia
    "హిందూ" to R.string.religion_hindu, // Telugu
    "हिंदू" to R.string.religion_hindu, // Marathi
    "હિંદુ" to R.string.religion_hindu, // Gujarati
    "ಹಿಂದೂ" to R.string.religion_hindu, // Kannada
    "ഹിന്ദു" to R.string.religion_hindu, // Malayalam
    "হিন্দু" to R.string.religion_hindu, // Assamese
    "ਹਿੰਦੂ" to R.string.religion_hindu, // Punjabi

    // Muslim
    "Muslim" to R.string.religion_muslim,
    "মুসলিম" to R.string.religion_muslim, // Bengali
    "मुस्लिम" to R.string.religion_muslim, // Hindi
    "முஸ்லிம்" to R.string.religion_muslim, // Tamil
    "ମୁସଲମାନ" to R.string.religion_muslim, // Odia
    "ముస్లిం" to R.string.religion_muslim, // Telugu
    "मुस्लिम" to R.string.religion_muslim, // Marathi
    "મુસ્લિમ" to R.string.religion_muslim, // Gujarati
    "ಮುಸ್ಲಿಂ" to R.string.religion_muslim, // Kannada
    "മുസ്ലിം" to R.string.religion_muslim, // Malayalam
    "মুছলিম" to R.string.religion_muslim, // Assamese
    "ਮੁਸਲਮਾਨ" to R.string.religion_muslim, // Punjabi

    // Christian
    "Christian" to R.string.religion_christian,
    "খ্রিস্টান" to R.string.religion_christian, // Bengali
    "ईसाई" to R.string.religion_christian, // Hindi
    "கிறிஸ்தவர்" to R.string.religion_christian, // Tamil
    "ଖ୍ରୀଷ୍ଟିଆନ" to R.string.religion_christian, // Odia
    "క్రైస్తవ" to R.string.religion_christian, // Telugu
    "ख्रिश्चन" to R.string.religion_christian, // Marathi
    "ખ્રિસ્તી" to R.string.religion_christian, // Gujarati
    "ಕ್ರಿಶ್ಚಿಯನ್" to R.string.religion_christian, // Kannada
    "ക്രിസ്ത്യൻ" to R.string.religion_christian, // Malayalam
    "খ্ৰীষ্টিয়ান" to R.string.religion_christian, // Assamese
    "ਈਸਾਈ" to R.string.religion_christian, // Punjabi

    // Buddhist
    "Buddhist" to R.string.religion_buddhist,
    "বৌদ্ধ" to R.string.religion_buddhist, // Bengali
    "बौद्ध" to R.string.religion_buddhist, // Hindi
    "பௌத்தர்" to R.string.religion_buddhist, // Tamil
    "ବୌଦ୍ଧ" to R.string.religion_buddhist, // Odia
    "బౌద్ధ" to R.string.religion_buddhist, // Telugu
    "बौद्ध" to R.string.religion_buddhist, // Marathi
    "બૌદ્ધ" to R.string.religion_buddhist, // Gujarati
    "ಬೌದ್ಧ" to R.string.religion_buddhist, // Kannada
    "ബുദ്ധ" to R.string.religion_buddhist, // Malayalam
    "বৌদ্ধ" to R.string.religion_buddhist, // Assamese
    "ਬੁੱਧ" to R.string.religion_buddhist, // Punjabi

    // Jain
    "Jain" to R.string.religion_jain,
    "জৈন" to R.string.religion_jain, // Bengali
    "जैन" to R.string.religion_jain, // Hindi
    "ஜைன" to R.string.religion_jain, // Tamil
    "ଜୈନ" to R.string.religion_jain, // Odia
    "జైన" to R.string.religion_jain, // Telugu
    "जैन" to R.string.religion_jain, // Marathi
    "જૈન" to R.string.religion_jain, // Gujarati
    "ಜೈನ" to R.string.religion_jain, // Kannada
    "ജൈന" to R.string.religion_jain, // Malayalam
    "জৈন" to R.string.religion_jain, // Assamese
    "ਜੈਨ" to R.string.religion_jain, // Punjabi

    // Sikh
    "Sikh" to R.string.religion_sikh,
    "সিখ" to R.string.religion_sikh, // Bengali
    "सिख" to R.string.religion_sikh, // Hindi
    "சீக்கியர்" to R.string.religion_sikh, // Tamil
    "ଶିଖ" to R.string.religion_sikh, // Odia
    "సిక్కు" to R.string.religion_sikh, // Telugu
    "शीख" to R.string.religion_sikh, // Marathi
    "શીખ" to R.string.religion_sikh, // Gujarati
    "ಸಿಖ್" to R.string.religion_sikh, // Kannada
    "സിഖ്" to R.string.religion_sikh, // Malayalam
    "শিখ" to R.string.religion_sikh, // Assamese
    "ਸਿੱਖ" to R.string.religion_sikh, // Punjabi

    // Jew
    "Jew" to R.string.religion_jewish,
    "ইহুদি" to R.string.religion_jewish, // Bengali
    "यहूदी" to R.string.religion_jewish, // Hindi
    "யூதர்" to R.string.religion_jewish, // Tamil
    "ଯିହୁଦୀ" to R.string.religion_jewish, // Odia
    "యూదు" to R.string.religion_jewish, // Telugu
    "यहूदी" to R.string.religion_jewish, // Marathi
    "યહૂદી" to R.string.religion_jewish, // Gujarati
    "ಯಹೂದಿ" to R.string.religion_jewish, // Kannada
    "യഹൂദ" to R.string.religion_jewish, // Malayalam
    "ইহুদী" to R.string.religion_jewish, // Assamese
    "ਯਹੂਦੀ" to R.string.religion_jewish, // Punjabi

    // Indigenous/Tribal
    "Indigenous/Tribal" to R.string.religion_indigenous_tribal,
    "আদিবাসী/উপজাতি" to R.string.religion_indigenous_tribal, // Bengali
    "स्वदेशी/जनजातीय" to R.string.religion_indigenous_tribal, // Hindi
    "பழங்குடி/பழங்குடியினர்" to R.string.religion_indigenous_tribal, // Tamil
    "ଆଦିବାସୀ/ଜନଜାତୀୟ" to R.string.religion_indigenous_tribal, // Odia
    "స్వదేశీ/తెగలు" to R.string.religion_indigenous_tribal, // Telugu
    "मूळ/आदिवासी" to R.string.religion_indigenous_tribal, // Marathi
    "સ્વદેશી/આદિવાસી" to R.string.religion_indigenous_tribal, // Gujarati
    "ಸ್ವದೇಶಿ/ಬುಡಕಟ್ಟು" to R.string.religion_indigenous_tribal, // Kannada
    "നാട:ൻ/|വ:ശ" to R.string.religion_indigenous_tribal, // Malayalam
    "আদিবাসী/উপজাতি" to R.string.religion_indigenous_tribal, // Assamese
    "ਸਵਦੇਸੀ/ਕਬੀਲੇ" to R.string.religion_indigenous_tribal, // Punjabi

    // No Religion
    "No Religion" to R.string.religion_no_religion,
    "কোনো ধর্ম নেই" to R.string.religion_no_religion, // Bengali
    "कोई धर्म नहीं" to R.string.religion_no_religion, // Hindi
    "மதமில்லை" to R.string.religion_no_religion, // Tamil
    "କୌଣସି ଧର୍ମ ନାହିଁ" to R.string.religion_no_religion, // Odia
    "మతం లేదు" to R.string.religion_no_religion, // Telugu
    "कोणताही धर्म नाही" to R.string.religion_no_religion, // Marathi
    "કોઈ ધર્મ નથી" to R.string.religion_no_religion, // Gujarati
    "ಯಾವುದೇ ಧರ್ಮವಿಲ" to R.string.religion_no_religion, // Kannada
    "മതമില" to R.string.religion_no_religion, // Malayalam
    "কোনো ধৰ্ম নাই" to R.string.religion_no_religion, // Assamese
    "ਕੋਈ ਧਰਮ ਨਹੀਂ" to R.string.religion_no_religion, // Punjabi

    // Zoroastrian
    "Zoroastrian" to R.string.religion_parsi,
    "জরথ্রুস্ট্রিয়ান" to R.string.religion_parsi, // Bengali
    "पारसी" to R.string.religion_parsi, // Hindi
    "ஜரோஸ்ட்ரியன்" to R.string.religion_parsi, // Tamil
    "ଜୋରୋଆଷ୍ଟ୍ରିଆନ" to R.string.religion_parsi, // Odia
    "జొరాస్ట్రియన్" to R.string.religion_parsi, // Telugu
    "पारशी" to R.string.religion_parsi, // Marathi
    "ઝોરોસ્ટ્રિયન" to R.string.religion_parsi, // Gujarati
    "ಜೊರಾಸ್ಟ್ರಿಯನ್" to R.string.religion_parsi, // Kannada
    "സോറോസ്‌ട്രിയൻ" to R.string.religion_parsi, // Malayalam
    "জৰোষ্ট্ৰিয়ান" to R.string.religion_parsi, // Assamese
    "ਜ਼ੋਰੋਆਸਟਰੀ" to R.string.religion_parsi, // Punjabi

    // Other
    "Other" to R.string.religion_other,
    "অন্যান্য" to R.string.religion_other, // Bengali
    "अन्य" to R.string.religion_other, // Hindi
    "மற்றவை" to R.string.religion_other, // Tamil
    "ଅନ୍ୟାନେ" to R.string.religion_other, // Odia
    "ఇతర" to R.string.religion_other, // Telugu
    "इतर" to R.string.religion_other, // Marathi
    "અન્ય" to R.string.religion_other, // Gujarati
    "ಇತರ" to R.string.religion_other, // Kannada
    "മറ്റുള്ളവ" to R.string.religion_other, // Malayalam
    "অন্যান্য" to R.string.religion_other, // Assamese
    "ਹੋਰ" to R.string.religion_other // Punjabi
)

val cityNameToRes = mapOf(
    // Agartala
    "Agartala" to R.string.city_agartala,
    "আগরতলা" to R.string.city_agartala, // Bengali
    "अगरतला" to R.string.city_agartala, // Hindi
    "அகர்தலா" to R.string.city_agartala, // Tamil
    "ଆଗରତାଲା" to R.string.city_agartala, // Odia
    "అగర్తలా" to R.string.city_agartala, // Telugu
    "अगर्तला" to R.string.city_agartala, // Marathi
    "અગરતલા" to R.string.city_agartala, // Gujarati
    "ಅಗರ್ತಲಾ" to R.string.city_agartala, // Kannada
    "അഗർതല" to R.string.city_agartala, // Malayalam
    "আগৰতলা" to R.string.city_agartala, // Assamese
    "ਅਗਰਤਲਾ" to R.string.city_agartala, // Punjabi

    // Ahmedabad
    "Ahmedabad" to R.string.city_ahmedabad,
    "আহমেদাবাদ" to R.string.city_ahmedabad, // Bengali
    "अहमदाबाद" to R.string.city_ahmedabad, // Hindi
    "அகமதாபாத்" to R.string.city_ahmedabad, // Tamil
    "ଅହମଦାବାଦ" to R.string.city_ahmedabad, // Odia
    "అహ్మదాబాద్" to R.string.city_ahmedabad, // Telugu
    "अहमदाबाद" to R.string.city_ahmedabad, // Marathi
    "અમદાવાદ" to R.string.city_ahmedabad, // Gujarati
    "ಅಹಮದಾಬಾದ್" to R.string.city_ahmedabad, // Kannada
    "അഹമ്മദാബാദ്" to R.string.city_ahmedabad, // Malayalam
    "আহমেদাবাদ" to R.string.city_ahmedabad, // Assamese
    "ਅਹਿਮਦਾਬਾਦ" to R.string.city_ahmedabad, // Punjabi

    // Aizawl
    "Aizawl" to R.string.city_aizawl,
    "আইজল" to R.string.city_aizawl, // Bengali
    "आइजोल" to R.string.city_aizawl, // Hindi
    "ஐஸ்வால்" to R.string.city_aizawl, // Tamil
    "ଆଇଜୋଲ" to R.string.city_aizawl, // Odia
    "ఐజ్వాల్" to R.string.city_aizawl, // Telugu
    "आयझॉल" to R.string.city_aizawl, // Marathi
    "આઈઝોલ" to R.string.city_aizawl, // Gujarati
    "ಐಜ್ವಾಲ್" to R.string.city_aizawl, // Kannada
    "ഐസ്വാൾ" to R.string.city_aizawl, // Malayalam
    "আইজল" to R.string.city_aizawl, // Assamese
    "ਆਈਜ਼ੌਲ" to R.string.city_aizawl, // Punjabi

    // Amaravati
    "Amaravati" to R.string.city_amaravati,
    "অমরাবতী" to R.string.city_amaravati, // Bengali
    "अमरावती" to R.string.city_amaravati, // Hindi
    "அமராவதி" to R.string.city_amaravati, // Tamil
    "ଅମରାବତୀ" to R.string.city_amaravati, // Odia
    "అమరావతి" to R.string.city_amaravati, // Telugu
    "अमरावती" to R.string.city_amaravati, // Marathi
    "અમરાવતી" to R.string.city_amaravati, // Gujarati
    "ಅಮರಾವತಿ" to R.string.city_amaravati, // Kannada
    "അമരാവതി" to R.string.city_amaravati, // Malayalam
    "অমৰাৱতী" to R.string.city_amaravati, // Assamese
    "ਅਮਰਾਵਤੀ" to R.string.city_amaravati, // Punjabi

    // Amritsar
    "Amritsar" to R.string.city_amritsar,
    "অমৃতসর" to R.string.city_amritsar, // Bengali
    "अमृतसर" to R.string.city_amritsar, // Hindi
    "அம்ரித்சர்" to R.string.city_amritsar, // Tamil
    "ଅମୃତସର" to R.string.city_amritsar, // Odia
    "అమృత్‌సర్" to R.string.city_amritsar, // Telugu
    "अमृतसर" to R.string.city_amritsar, // Marathi
    "અમૃતસર" to R.string.city_amritsar, // Gujarati
    "ಅಮೃತಸರ್" to R.string.city_amritsar, // Kannada
    "അമൃത്സർ" to R.string.city_amritsar, // Malayalam
    "অমৃতসৰ" to R.string.city_amritsar, // Assamese
    "ਅੰਮ੍ਰਿਤਸਰ" to R.string.city_amritsar, // Punjabi

    // Asansol
    "Asansol" to R.string.city_asansol,
    "আসানসোল" to R.string.city_asansol, // Bengali
    "आसनसोल" to R.string.city_asansol, // Hindi
    "ஆசன்சோல்" to R.string.city_asansol, // Tamil
    "ଆସାନସୋଲ" to R.string.city_asansol, // Odia
    "ఆసన్‌సోల్" to R.string.city_asansol, // Telugu
    "आसनसोल" to R.string.city_asansol, // Marathi
    "આસનસોલ" to R.string.city_asansol, // Gujarati
    "ಆಸನ್‌ಸೋಲ್" to R.string.city_asansol, // Kannada
    "ആസൻസോൾ" to R.string.city_asansol, // Malayalam
    "আসানচোল" to R.string.city_asansol, // Assamese
    "ਆਸਨਸੋਲ" to R.string.city_asansol, // Punjabi

    // Bengaluru
    "Bengaluru" to R.string.city_bengaluru,
    "বেঙ্গালুরু" to R.string.city_bengaluru, // Bengali
    "बेंगलुरु" to R.string.city_bengaluru, // Hindi
    "பெங்களூரு" to R.string.city_bengaluru, // Tamil
    "ବେଙ୍ଗାଲୁରୁ" to R.string.city_bengaluru, // Odia
    "బెంగళూరు" to R.string.city_bengaluru, // Telugu
    "बेंगलुरू" to R.string.city_bengaluru, // Marathi
    "બેંગલુરુ" to R.string.city_bengaluru, // Gujarati
    "ಬೆಂಗಳೂರು" to R.string.city_bengaluru, // Kannada
    "ബെംഗളൂരു" to R.string.city_bengaluru, // Malayalam
    "বেংগালুৰু" to R.string.city_bengaluru, // Assamese
    "ਬੰਗਲੌਰ" to R.string.city_bengaluru, // Punjabi

    // Bhilai
    "Bhilai" to R.string.city_bhilai,
    "ভিলাই" to R.string.city_bhilai, // Bengali
    "भिलाई" to R.string.city_bhilai, // Hindi
    "பிலாய்" to R.string.city_bhilai, // Tamil
    "ଭିଲାଇ" to R.string.city_bhilai, // Odia
    "భిలాయ్" to R.string.city_bhilai, // Telugu
    "भिलाई" to R.string.city_bhilai, // Marathi
    "ભિલાઈ" to R.string.city_bhilai, // Gujarati
    "ಭಿಲಾಯ್" to R.string.city_bhilai, // Kannada
    "ഭിലായ്" to R.string.city_bhilai, // Malayalam
    "ভিলাই" to R.string.city_bhilai, // Assamese
    "ਭਿਲਾਈ" to R.string.city_bhilai, // Punjabi

    // Bhopal
    "Bhopal" to R.string.city_bhopal,
    "ভোপাল" to R.string.city_bhopal, // Bengali
    "भोपाल" to R.string.city_bhopal, // Hindi
    "போபால்" to R.string.city_bhopal, // Tamil
    "ଭୋପାଳ" to R.string.city_bhopal, // Odia
    "భోపాల్" to R.string.city_bhopal, // Telugu
    "भोपाळ" to R.string.city_bhopal, // Marathi
    "ભોપાલ" to R.string.city_bhopal, // Gujarati
    "ಭೋಪಾಲ್" to R.string.city_bhopal, // Kannada
    "ഭോപ്പാൽ" to R.string.city_bhopal, // Malayalam
    "ভোপাল" to R.string.city_bhopal, // Assamese
    "ਭੋਪਾਲ" to R.string.city_bhopal, // Punjabi

    // Bhubaneswar
    "Bhubaneswar" to R.string.city_bhubaneswar,
    "ভুবনেশ্বর" to R.string.city_bhubaneswar, // Bengali
    "भुवनेश्वर" to R.string.city_bhubaneswar, // Hindi
    "புவனேஸ்வர்" to R.string.city_bhubaneswar, // Tamil
    "ଭୁବନେଶ୍ୱର" to R.string.city_bhubaneswar, // Odia
    "భువనేశ్వర్" to R.string.city_bhubaneswar, // Telugu
    "भुवनेश्वर" to R.string.city_bhubaneswar, // Marathi
    "ભુવનેશ્વર" to R.string.city_bhubaneswar, // Gujarati
    "ಭುವನೇಶ್ವರ್" to R.string.city_bhubaneswar, // Kannada
    "ഭുവനേശ്വർ" to R.string.city_bhubaneswar, // Malayalam
    "ভুৱনেশ্বৰ" to R.string.city_bhubaneswar, // Assamese
    "ਭੁਵਨੇਸ਼ਵਰ" to R.string.city_bhubaneswar, // Punjabi

    // Bilaspur
    "Bilaspur" to R.string.city_bilaspur,
    "বিলাসপুর" to R.string.city_bilaspur, // Bengali
    "बिलासपुर" to R.string.city_bilaspur, // Hindi
    "பிலாஸ்பூர்" to R.string.city_bilaspur, // Tamil
    "ବିଲାସପୁର" to R.string.city_bilaspur, // Odia
    "బిలాస్‌పూర్" to R.string.city_bilaspur, // Telugu
    "बिलासपूर" to R.string.city_bilaspur, // Marathi
    "બિલાસપુર" to R.string.city_bilaspur, // Gujarati
    "ಬಿಲಾಸ್‌ಪುರ್" to R.string.city_bilaspur, // Kannada
    "ബിലാസ്പൂർ" to R.string.city_bilaspur, // Malayalam
    "বিলাসপুৰ" to R.string.city_bilaspur, // Assamese
    "ਬਿਲਾਸਪੁਰ" to R.string.city_bilaspur, // Punjabi

    // Chandigarh
    "Chandigarh" to R.string.city_chandigarh,
    "চণ্ডীগড়" to R.string.city_chandigarh, // Bengali
    "चंडीगढ़" to R.string.city_chandigarh, // Hindi
    "சண்டிகர்" to R.string.city_chandigarh, // Tamil
    "ଚଣ୍ଡୀଗଡ଼" to R.string.city_chandigarh, // Odia
    "చండీగఢ్" to R.string.city_chandigarh, // Telugu
    "चंदीगड" to R.string.city_chandigarh, // Marathi
    "ચંડીગઢ" to R.string.city_chandigarh, // Gujarati
    "ಚಂಡೀಗಢ" to R.string.city_chandigarh, // Kannada
    "ചണ്ഡിഗഢ്" to R.string.city_chandigarh, // Malayalam
    "চণ্ডীগড়" to R.string.city_chandigarh, // Assamese
    "ਚੰਡੀਗੜ੍ਹ" to R.string.city_chandigarh, // Punjabi

    // Chennai
    "Chennai" to R.string.city_chennai,
    "চেন্নাই" to R.string.city_chennai, // Bengali
    "चेन्नई" to R.string.city_chennai, // Hindi
    "சென்னை" to R.string.city_chennai, // Tamil
    "ଚେନ୍ନାଇ" to R.string.city_chennai, // Odia
    "చెన్నై" to R.string.city_chennai, // Telugu
    "चेन्नई" to R.string.city_chennai, // Marathi
    "ચેન્નઈ" to R.string.city_chennai, // Gujarati
    "ಚೆನ್ನೈ" to R.string.city_chennai, // Kannada
    "ചെന്നൈ" to R.string.city_chennai, // Malayalam
    "চেন্নাই" to R.string.city_chennai, // Assamese
    "ਚੇਨਈ" to R.string.city_chennai, // Punjabi

    // Coimbatore
    "Coimbatore" to R.string.city_coimbatore,
    "কোয়েম্বাটুর" to R.string.city_coimbatore, // Bengali
    "कोयंबटूर" to R.string.city_coimbatore, // Hindi
    "கோயம்புத்தூர்" to R.string.city_coimbatore, // Tamil
    "କୋଇମ୍ବାଟୁର" to R.string.city_coimbatore, // Odia
    "కోయంబత్తూర్" to R.string.city_coimbatore, // Telugu
    "कोयंबटूर" to R.string.city_coimbatore, // Marathi
    "કોઈમ્બતુર" to R.string.city_coimbatore, // Gujarati
    "ಕೊಯಿಂಬತ್ತೂರ್" to R.string.city_coimbatore, // Kannada
    "കോയമ്പത്തൂർ" to R.string.city_coimbatore, // Malayalam
    "কোইম্বাটুৰ" to R.string.city_coimbatore, // Assamese
    "ਕੋਇੰਬਤੁਰ" to R.string.city_coimbatore, // Punjabi

    // Cuttack
    "Cuttack" to R.string.city_cuttack,
    "কটক" to R.string.city_cuttack, // Bengali
    "कटक" to R.string.city_cuttack, // Hindi
    "கட்டாக்" to R.string.city_cuttack, // Tamil
    "କଟକ" to R.string.city_cuttack, // Odia
    "కటక్" to R.string.city_cuttack, // Telugu
    "कटक" to R.string.city_cuttack, // Marathi
    "કટક" to R.string.city_cuttack, // Gujarati
    "ಕಟಕ್" to R.string.city_cuttack, // Kannada
    "കട്ടക്" to R.string.city_cuttack, // Malayalam
    "কটক" to R.string.city_cuttack, // Assamese
    "ਕਟਕ" to R.string.city_cuttack, // Punjabi

    // Daman
    "Daman" to R.string.city_daman,
    "দমন" to R.string.city_daman, // Bengali
    "दमन" to R.string.city_daman, // Hindi
    "தமன்" to R.string.city_daman, // Tamil
    "ଦମନ" to R.string.city_daman, // Odia
    "దమన్" to R.string.city_daman, // Telugu
    "दमण" to R.string.city_daman, // Marathi
    "દમણ" to R.string.city_daman, // Gujarati
    "ದಮನ್" to R.string.city_daman, // Kannada
    "ദമൻ" to R.string.city_daman, // Malayalam
    "দমন" to R.string.city_daman, // Assamese
    "ਦਮਨ" to R.string.city_daman, // Punjabi

    // Darjeeling
    "Darjeeling" to R.string.city_darjeeling,
    "দার্জিলিং" to R.string.city_darjeeling, // Bengali
    "दार्जिलिंग" to R.string.city_darjeeling, // Hindi
    "தார்ஜிலிங்" to R.string.city_darjeeling, // Tamil
    "ଦାରଜୀଲିଙ୍ଗ" to R.string.city_darjeeling, // Odia
    "డార్జిలింగ్" to R.string.city_darjeeling, // Telugu
    "दार्जिलिंग" to R.string.city_darjeeling, // Marathi
    "દાર્જિલિંગ" to R.string.city_darjeeling, // Gujarati
    "ದಾರ್ಜಿಲಿಂಗ್" to R.string.city_darjeeling, // Kannada
    "ഡാർജിലിംഗ്" to R.string.city_darjeeling, // Malayalam
    "দাৰ্জিলিং" to R.string.city_darjeeling, // Assamese
    "ਦਾਰਜੀਲਿੰਗ" to R.string.city_darjeeling, // Punjabi

    // Dehradun
    "Dehradun" to R.string.city_dehradun,
    "দেহরাদুন" to R.string.city_dehradun, // Bengali
    "देहरादून" to R.string.city_dehradun, // Hindi
    "டேராடூன்" to R.string.city_dehradun, // Tamil
    "ଦେହରାଦୂନ" to R.string.city_dehradun, // Odia
    "డెహ్రాడూన్" to R.string.city_dehradun, // Telugu
    "देहरादून" to R.string.city_dehradun, // Marathi
    "દેહરાદૂન" to R.string.city_dehradun, // Gujarati
    "ದೆಹರಾದೂನ್" to R.string.city_dehradun, // Kannada
    "ഡെറാഡൂൺ" to R.string.city_dehradun, // Malayalam
    "দেহৰাদুন" to R.string.city_dehradun, // Assamese
    "ਦੇਹਰਾਦੂਨ" to R.string.city_dehradun, // Punjabi

    // Dibrugarh
    "Dibrugarh" to R.string.city_dibrugarh,
    "ডিব্রুগড়" to R.string.city_dibrugarh, // Bengali
    "डिब्रूगढ़" to R.string.city_dibrugarh, // Hindi
    "திப்ரூகர்" to R.string.city_dibrugarh, // Tamil
    "ଡିବ୍ରୁଗଡ଼" to R.string.city_dibrugarh, // Odia
    "డిబ్రూగఢ్" to R.string.city_dibrugarh, // Telugu
    "डिब्रूगड" to R.string.city_dibrugarh, // Marathi
    "ડિબ્રુગઢ" to R.string.city_dibrugarh, // Gujarati
    "ಡಿಬ್ರೂಗಢ್" to R.string.city_dibrugarh, // Kannada
    "ഡിബ്രുഗഢ്" to R.string.city_dibrugarh, // Malayalam
    "ডিব্ৰুগড়" to R.string.city_dibrugarh, // Assamese
    "ਡਿਬਰੂਗੜ੍ਹ" to R.string.city_dibrugarh, // Punjabi

    // Dharamshala
    "Dharamshala" to R.string.city_dharamshala,
    "ধরমশালা" to R.string.city_dharamshala, // Bengali
    "धर्मशाला" to R.string.city_dharamshala, // Hindi
    "தர்மசாலா" to R.string.city_dharamshala, // Tamil
    "ଧର୍ମଶାଳା" to R.string.city_dharamshala, // Odia
    "ధర్మశాల" to R.string.city_dharamshala, // Telugu
    "धर्मशाला" to R.string.city_dharamshala, // Marathi
    "ધર્મશાળા" to R.string.city_dharamshala, // Gujarati
    "ಧರ್ಮಶಾಲಾ" to R.string.city_dharamshala, // Kannada
    "ധർമ്മശാല" to R.string.city_dharamshala, // Malayalam
    "ধৰ্মশালা" to R.string.city_dharamshala, // Assamese
    "ਧਰਮਸ਼ਾਲਾ" to R.string.city_dharamshala, // Punjabi

    // Durgapur
    "Durgapur" to R.string.city_durgapur,
    "দুর্গাপুর" to R.string.city_durgapur, // Bengali
    "दुर्गापुर" to R.string.city_durgapur, // Hindi
    "துர்காபூர்" to R.string.city_durgapur, // Tamil
    "ଦୁର୍ଗାପୁର" to R.string.city_durgapur, // Odia
    "దుర్గాపూర్" to R.string.city_durgapur, // Telugu
    "दुर्गापूर" to R.string.city_durgapur, // Marathi
    "દુર્ગાપુર" to R.string.city_durgapur, // Gujarati
    "ದುರ್ಗಾಪುರ್" to R.string.city_durgapur, // Kannada
    "ദുർഗാപൂർ" to R.string.city_durgapur, // Malayalam
    "দুৰ্গাপুৰ" to R.string.city_durgapur, // Assamese
    "ਦੁਰਗਾਪੁਰ" to R.string.city_durgapur, // Punjabi

    // Faridabad
    "Faridabad" to R.string.city_faridabad,
    "ফরিদাবাদ" to R.string.city_faridabad, // Bengali
    "फरीदाबाद" to R.string.city_faridabad, // Hindi
    "பரிதாபாத்" to R.string.city_faridabad, // Tamil
    "ଫରିଦାବାଦ" to R.string.city_faridabad, // Odia
    "ఫరీదాబాద్" to R.string.city_faridabad, // Telugu
    "फरीदाबाद" to R.string.city_faridabad, // Marathi
    "ફરીદાબાદ" to R.string.city_faridabad, // Gujarati
    "ಫರೀದಾಬಾದ್" to R.string.city_faridabad, // Kannada
    "ഫരീദാബാദ്" to R.string.city_faridabad, // Malayalam
    "ফৰিদাবাদ" to R.string.city_faridabad, // Assamese
    "ਫਰੀਦਾਬਾਦ" to R.string.city_faridabad, // Punjabi

    // Gangtok
    "Gangtok" to R.string.city_gangtok,
    "গ্যাংটক" to R.string.city_gangtok, // Bengali
    "गंगटोक" to R.string.city_gangtok, // Hindi
    "காங்டாக்" to R.string.city_gangtok, // Tamil
    "ଗାଙ୍ଗଟକ" to R.string.city_gangtok, // Odia
    "గ్యాంగ్‌టాక్" to R.string.city_gangtok, // Telugu
    "गंगटोक" to R.string.city_gangtok, // Marathi
    "ગેંગટોક" to R.string.city_gangtok, // Gujarati
    "ಗ್ಯಾಂಗ್‌ಟಾಕ್" to R.string.city_gangtok, // Kannada
    "ഗാങ്‌ടോക്" to R.string.city_gangtok, // Malayalam
    "গেংটক" to R.string.city_gangtok, // Assamese
    "ਗੰਗਟੋਕ" to R.string.city_gangtok, // Punjabi

    // Gaya
    "Gaya" to R.string.city_gaya,
    "গয়া" to R.string.city_gaya, // Bengali
    "गया" to R.string.city_gaya, // Hindi
    "கயா" to R.string.city_gaya, // Tamil
    "ଗୟା" to R.string.city_gaya, // Odia
    "గయా" to R.string.city_gaya, // Telugu
    "गया" to R.string.city_gaya, // Marathi
    "ગયા" to R.string.city_gaya, // Gujarati
    "ಗಯಾ" to R.string.city_gaya, // Kannada
    "ഗയ" to R.string.city_gaya, // Malayalam
    "গয়া" to R.string.city_gaya, // Assamese
    "ਗਯਾ" to R.string.city_gaya, // Punjabi

    // Gandhinagar
    "Gandhinagar" to R.string.city_gandhinagar,
    "গান্ধীনগর" to R.string.city_gandhinagar, // Bengali
    "गांधीनगर" to R.string.city_gandhinagar, // Hindi
    "காந்திநகர்" to R.string.city_gandhinagar, // Tamil
    "ଗାନ୍ଧୀନଗର" to R.string.city_gandhinagar, // Odia
    "గాంధీనగర్" to R.string.city_gandhinagar, // Telugu
    "गांधीनगर" to R.string.city_gandhinagar, // Marathi
    "ગાંધીનગર" to R.string.city_gandhinagar, // Gujarati
    "ಗಾಂಧಿನಗರ್" to R.string.city_gandhinagar, // Kannada
    "ഗാന്ധിനഗർ" to R.string.city_gandhinagar, // Malayalam
    "গান্ধীনগৰ" to R.string.city_gandhinagar, // Assamese
    "ਗਾਂਧੀਨਗਰ" to R.string.city_gandhinagar, // Punjabi

    // Ghaziabad
    "Ghaziabad" to R.string.city_ghaziabad,
    "গাজিয়াবাদ" to R.string.city_ghaziabad, // Bengali
    "गाजियाबाद" to R.string.city_ghaziabad, // Hindi
    "காசியாபாத்" to R.string.city_ghaziabad, // Tamil
    "ଗାଜିଆବାଦ" to R.string.city_ghaziabad, // Odia
    "గాజియాబాద్" to R.string.city_ghaziabad, // Telugu
    "गाझियाबाद" to R.string.city_ghaziabad, // Marathi
    "ગાઝિયાબાદ" to R.string.city_ghaziabad, // Gujarati
    "ಗಾಜಿಯಾಬಾದ್" to R.string.city_ghaziabad, // Kannada
    "ഖാസിയാബാദ്" to R.string.city_ghaziabad, // Malayalam
    "গাজিয়াবাদ" to R.string.city_ghaziabad, // Assamese
    "ਗਾਜ਼ੀਆਬਾਦ" to R.string.city_ghaziabad, // Punjabi

    // Gwalior
    "Gwalior" to R.string.city_gwalior,
    "গোয়ালিয়র" to R.string.city_gwalior, // Bengali
    "ग्वालियर" to R.string.city_gwalior, // Hindi
    "குவாலியர்" to R.string.city_gwalior, // Tamil
    "ଗ୍ୱାଲିଅର" to R.string.city_gwalior, // Odia
    "గ్వాలియర్" to R.string.city_gwalior, // Telugu
    "ग्वाल्हेर" to R.string.city_gwalior, // Marathi
    "ગ્વાલિયર" to R.string.city_gwalior, // Gujarati
    "ಗ್ವಾಲಿಯರ್" to R.string.city_gwalior, // Kannada
    "ഗ്വാളിയോർ" to R.string.city_gwalior, // Malayalam
    "গোৱালিয়ৰ" to R.string.city_gwalior, // Assamese
    "ਗਵਾਲੀਅਰ" to R.string.city_gwalior, // Punjabi

    // Gyalshing
    "Gyalshing" to R.string.city_gyalshing,
    "গিয়ালশিং" to R.string.city_gyalshing, // Bengali
    "ग्यालशिंग" to R.string.city_gyalshing, // Hindi
    "கியால்ஷிங்" to R.string.city_gyalshing, // Tamil
    "ଗ୍ୟାଲସିଂ" to R.string.city_gyalshing, // Odia
    "గ్యాల్షింగ్" to R.string.city_gyalshing, // Telugu
    "ग्यालशिंग" to R.string.city_gyalshing, // Marathi
    "ગ્યાલશિંગ" to R.string.city_gyalshing, // Gujarati
    "ಗ್ಯಾಲ್ಶಿಂಗ್" to R.string.city_gyalshing, // Kannada
    "ഗ്യാൽഷിങ്" to R.string.city_gyalshing, // Malayalam
    "গিয়ালশিং" to R.string.city_gyalshing, // Assamese
    "ਗਿਆਲਸ਼ਿੰਗ" to R.string.city_gyalshing, // Punjabi

    // Guwahati
    "Guwahati" to R.string.city_guwahati,
    "গৌহাটি" to R.string.city_guwahati, // Bengali
    "गुवाहाटी" to R.string.city_guwahati, // Hindi
    "குவஹாதி" to R.string.city_guwahati, // Tamil
    "ଗୁୱାହାଟୀ" to R.string.city_guwahati, // Odia
    "గౌహతి" to R.string.city_guwahati, // Telugu
    "गुवाहाटी" to R.string.city_guwahati, // Marathi
    "ગુવાહાટી" to R.string.city_guwahati, // Gujarati
    "ಗುವಾಹಾಟಿ" to R.string.city_guwahati, // Kannada
    "ഗുവാഹത്തി" to R.string.city_guwahati, // Malayalam
    "গুৱাহাটী" to R.string.city_guwahati, // Assamese
    "ਗੁਵਾਹਾਟੀ" to R.string.city_guwahati, // Punjabi

    // Gurugram
    "Gurugram" to R.string.city_gurugram,
    "গুরুগ্রাম" to R.string.city_gurugram, // Bengali
    "गुरुग्राम" to R.string.city_gurugram, // Hindi
    "குருகிராம்" to R.string.city_gurugram, // Tamil
    "ଗୁରୁଗ୍ରାମ" to R.string.city_gurugram, // Odia
    "గురుగ్రామ్" to R.string.city_gurugram, // Telugu
    "गुरुग्राम" to R.string.city_gurugram, // Marathi
    "ગુરુગ્રામ" to R.string.city_gurugram, // Gujarati
    "ಗುರುಗ್ರಾಮ್" to R.string.city_gurugram, // Kannada
    "ഗുരുഗ്രാം" to R.string.city_gurugram, // Malayalam
    "গুৰুগ্ৰাম" to R.string.city_gurugram, // Assamese
    "ਗੁਰੂਗ੍ਰਾਮ" to R.string.city_gurugram, // Punjabi

    // Haridwar
    "Haridwar" to R.string.city_haridwar,
    "হরিদ্বার" to R.string.city_haridwar, // Bengali
    "हरिद्वार" to R.string.city_haridwar, // Hindi
    "ஹரித்வார்" to R.string.city_haridwar, // Tamil
    "ହରିଦ୍ୱାର" to R.string.city_haridwar, // Odia
    "హరిద్వార్" to R.string.city_haridwar, // Telugu
    "हरिद्वार" to R.string.city_haridwar, // Marathi
    "હરિદ્વાર" to R.string.city_haridwar, // Gujarati
    "ಹರಿದ್ವಾರ್" to R.string.city_haridwar, // Kannada
    "ഹരിദ്വാർ" to R.string.city_haridwar, // Malayalam
    "হৰিদ্বাৰ" to R.string.city_haridwar, // Assamese
    "ਹਰਿਦ੍ਵਾਰ" to R.string.city_haridwar, // Punjabi

    // Hisar
    "Hisar" to R.string.city_hisar,
    "হিসার" to R.string.city_hisar, // Bengali
    "हिसार" to R.string.city_hisar, // Hindi
    "ஹிசார்" to R.string.city_hisar, // Tamil
    "ହିସାର" to R.string.city_hisar, // Odia
    "హిసార్" to R.string.city_hisar, // Telugu
    "हिस्सार" to R.string.city_hisar, // Marathi
    "હિસાર" to R.string.city_hisar, // Gujarati
    "ಹಿಸಾರ್" to R.string.city_hisar, // Kannada
    "ഹിസാർ" to R.string.city_hisar, // Malayalam
    "হিসাৰ" to R.string.city_hisar, // Assamese
    "ਹਿਸਾਰ" to R.string.city_hisar, // Punjabi

    // Howrah
    "Howrah" to R.string.city_howrah,
    "হাওড়া" to R.string.city_howrah, // Bengali
    "हावड़ा" to R.string.city_howrah, // Hindi
    "ஹவுரா" to R.string.city_howrah, // Tamil
    "ହାଓଡ଼ା" to R.string.city_howrah, // Odia
    "హౌరా" to R.string.city_howrah, // Telugu
    "हावडा" to R.string.city_howrah, // Marathi
    "હાવડા" to R.string.city_howrah, // Gujarati
    "ಹೌರಾ" to R.string.city_howrah, // Kannada
    "ഹൗറ" to R.string.city_howrah, // Malayalam
    "হাওৰা" to R.string.city_howrah, // Assamese
    "ਹਾਵੜਾ" to R.string.city_howrah, // Punjabi

    // Hyderabad
    "Hyderabad" to R.string.city_hyderabad,
    "হায়দ্রাবাদ" to R.string.city_hyderabad, // Bengali
    "हैदराबाद" to R.string.city_hyderabad, // Hindi
    "ஹைதராபாத்" to R.string.city_hyderabad, // Tamil
    "ହାଇଦ୍ରାବାଦ" to R.string.city_hyderabad, // Odia
    "హైదరాబాద్" to R.string.city_hyderabad, // Telugu
    "हैदराबाद" to R.string.city_hyderabad, // Marathi
    "હૈદરાબાદ" to R.string.city_hyderabad, // Gujarati
    "ಹೈದರಾಬಾದ್" to R.string.city_hyderabad, // Kannada
    "ഹൈദരാബാദ്" to R.string.city_hyderabad, // Malayalam
    "হায়দ্ৰাবাদ" to R.string.city_hyderabad, // Assamese
    "ਹੈਦਰਾਬਾਦ" to R.string.city_hyderabad, // Punjabi

    // Imphal
    "Imphal" to R.string.city_imphal,
    "ইম্ফল" to R.string.city_imphal, // Bengali
    "इंफाल" to R.string.city_imphal, // Hindi
    "இம்பால்" to R.string.city_imphal, // Tamil
    "ଇମ୍ଫାଲ" to R.string.city_imphal, // Odia
    "ఇంఫాల్" to R.string.city_imphal, // Telugu
    "इंफाळ" to R.string.city_imphal, // Marathi
    "ઈમ્ફાલ" to R.string.city_imphal, // Gujarati
    "ಇಂಫಾಲ್" to R.string.city_imphal, // Kannada
    "ഇംഫാൽ" to R.string.city_imphal, // Malayalam
    "ইম্ফল" to R.string.city_imphal, // Assamese
    "ਇੰਫਾਲ" to R.string.city_imphal, // Punjabi

    // Indore
    "Indore" to R.string.city_indore,
    "ইন্দোর" to R.string.city_indore, // Bengali
    "इंदौर" to R.string.city_indore, // Hindi
    "இந்தோர்" to R.string.city_indore, // Tamil
    "ଇନ୍ଦୋର" to R.string.city_indore, // Odia
    "ఇండోర్" to R.string.city_indore, // Telugu
    "इंदूर" to R.string.city_indore, // Marathi
    "ઈન્દોર" to R.string.city_indore, // Gujarati
    "ಇಂದೋರ್" to R.string.city_indore, // Kannada
    "ഇൻഡോർ" to R.string.city_indore, // Malayalam
    "ইন্দোৰ" to R.string.city_indore, // Assamese
    "ਇੰਦੌਰ" to R.string.city_indore, // Punjabi

    // Itanagar
    "Itanagar" to R.string.city_itanagar,
    "ইটানগর" to R.string.city_itanagar, // Bengali
    "ईटानगर" to R.string.city_itanagar, // Hindi
    "இடாநகர்" to R.string.city_itanagar, // Tamil
    "ଇଟାନଗର" to R.string.city_itanagar, // Odia
    "ఇటానగర్" to R.string.city_itanagar, // Telugu
    "इटानगर" to R.string.city_itanagar, // Marathi
    "ઈટાનગર" to R.string.city_itanagar, // Gujarati
    "ಇಟಾನಗರ್" to R.string.city_itanagar, // Kannada
    "ഇറ്റാനഗർ" to R.string.city_itanagar, // Malayalam
    "ইটানগৰ" to R.string.city_itanagar, // Assamese
    "ਇਟਾਨਗਰ" to R.string.city_itanagar, // Punjabi

    // Jaipur
    "Jaipur" to R.string.city_jaipur,
    "জয়পুর" to R.string.city_jaipur, // Bengali
    "जयपुर" to R.string.city_jaipur, // Hindi
    "ஜெய்ப்பூர்" to R.string.city_jaipur, // Tamil
    "ଜୟପୁର" to R.string.city_jaipur, // Odia
    "జైపూర్" to R.string.city_jaipur, // Telugu
    "जयपूर" to R.string.city_jaipur, // Marathi
    "જયપુર" to R.string.city_jaipur, // Gujarati
    "ಜೈಪುರ್" to R.string.city_jaipur, // Kannada
    "ജയ്‌പൂർ" to R.string.city_jaipur, // Malayalam
    "জয়পুৰ" to R.string.city_jaipur, // Assamese
    "ਜੈਪੁਰ" to R.string.city_jaipur, // Punjabi

    // Jamshedpur
    "Jamshedpur" to R.string.city_jamshedpur,
    "জামশেদপুর" to R.string.city_jamshedpur, // Bengali
    "जमशेदपुर" to R.string.city_jamshedpur, // Hindi
    "ஜம்ஷெட்பூர்" to R.string.city_jamshedpur, // Tamil
    "ଜାମଶେଦପୁର" to R.string.city_jamshedpur, // Odia
    "జంషెడ్‌పూర్" to R.string.city_jamshedpur, // Telugu
    "जमशेदपूर" to R.string.city_jamshedpur, // Marathi
    "જમશેદપુર" to R.string.city_jamshedpur, // Gujarati
    "ಜಂಶೇದ್‌ಪುರ್" to R.string.city_jamshedpur, // Kannada
    "ജംഷെഡ്‌പൂർ" to R.string.city_jamshedpur, // Malayalam
    "জামশেদপুৰ" to R.string.city_jamshedpur, // Assamese
    "ਜਮਸ਼ੇਦਪੁਰ" to R.string.city_jamshedpur, // Punjabi

    // Jodhpur
    "Jodhpur" to R.string.city_jodhpur,
    "জোধপুর" to R.string.city_jodhpur, // Bengali
    "जोधपुर" to R.string.city_jodhpur, // Hindi
    "ஜோத்பூர்" to R.string.city_jodhpur, // Tamil
    "ଜୋଧପୁର" to R.string.city_jodhpur, // Odia
    "జోధ్‌పూర్" to R.string.city_jodhpur, // Telugu
    "जोधपूर" to R.string.city_jodhpur, // Marathi
    "જોધપુર" to R.string.city_jodhpur, // Gujarati
    "ಜೋಧ್‌ಪುರ್" to R.string.city_jodhpur, // Kannada
    "ജോധ്‌പൂർ" to R.string.city_jodhpur, // Malayalam
    "জোধপুৰ" to R.string.city_jodhpur, // Assamese
    "ਜੋਧਪੁਰ" to R.string.city_jodhpur, // Punjabi

    // Kancheepuram
    "Kancheepuram" to R.string.city_kancheepuram,
    "কাঞ্চিপুরম" to R.string.city_kancheepuram, // Bengali
    "कांचीपुरम" to R.string.city_kancheepuram, // Hindi
    "காஞ்சிபுரம்" to R.string.city_kancheepuram, // Tamil
    "କାଞ୍ଚୀପୁରମ" to R.string.city_kancheepuram, // Odia
    "కాంచీపురం" to R.string.city_kancheepuram, // Telugu
    "कांचीपुरम" to R.string.city_kancheepuram, // Marathi
    "કાંચીપુરમ" to R.string.city_kancheepuram, // Gujarati
    "ಕಾಂಚೀಪುರಂ" to R.string.city_kancheepuram, // Kannada
    "കാഞ്ചിപുരം" to R.string.city_kancheepuram, // Malayalam
    "কাঞ্চীপুৰম" to R.string.city_kancheepuram, // Assamese
    "ਕਾਂਚੀਪੁਰਮ" to R.string.city_kancheepuram, // Punjabi

    // Kanpur
    "Kanpur" to R.string.city_kanpur,
    "কানপুর" to R.string.city_kanpur, // Bengali
    "कानपुर" to R.string.city_kanpur, // Hindi
    "கான்பூர்" to R.string.city_kanpur, // Tamil
    "କାନପୁର" to R.string.city_kanpur, // Odia
    "కాన్పూర్" to R.string.city_kanpur, // Telugu
    "कानपूर" to R.string.city_kanpur, // Marathi
    "કાનપુર" to R.string.city_kanpur, // Gujarati
    "ಕಾನ್ಪುರ್" to R.string.city_kanpur, // Kannada
    "കാൺപൂർ" to R.string.city_kanpur, // Malayalam
    "কানপুৰ" to R.string.city_kanpur, // Assamese
    "ਕਾਨਪੁਰ" to R.string.city_kanpur, // Punjabi

    // Kargil
    "Kargil" to R.string.city_kargil,
    "কার্গিল" to R.string.city_kargil, // Bengali
    "कारगिल" to R.string.city_kargil, // Hindi
    "கார்கில்" to R.string.city_kargil, // Tamil
    "କାରଗିଲ" to R.string.city_kargil, // Odia
    "కార్గిల్" to R.string.city_kargil, // Telugu
    "कारगिल" to R.string.city_kargil, // Marathi
    "કારગિલ" to R.string.city_kargil, // Gujarati
    "ಕಾರ್ಗಿಲ್" to R.string.city_kargil, // Kannada
    "കാർഗിൽ" to R.string.city_kargil, // Malayalam
    "কাৰ্গিল" to R.string.city_kargil, // Assamese
    "ਕਾਰਗਿਲ" to R.string.city_kargil, // Punjabi

    // Kavaratti
    "Kavaratti" to R.string.city_kavaratti,
    "কাভারত্তি" to R.string.city_kavaratti, // Bengali
    "कवरत्ती" to R.string.city_kavaratti, // Hindi
    "கவரத்தி" to R.string.city_kavaratti, // Tamil
    "କାଭାରତୀ" to R.string.city_kavaratti, // Odia
    "కవరత్తి" to R.string.city_kavaratti, // Telugu
    "कवरत्ती" to R.string.city_kavaratti, // Marathi
    "કવરત્તી" to R.string.city_kavaratti, // Gujarati
    "ಕವರತ್ತಿ" to R.string.city_kavaratti, // Kannada
    "കവരത്തി" to R.string.city_kavaratti, // Malayalam
    "কাভাৰত্তি" to R.string.city_kavaratti, // Assamese
    "ਕਵਰੱਤੀ" to R.string.city_kavaratti, // Punjabi

    // Kharagpur
    "Kharagpur" to R.string.city_kharagpur,
    "খড়গপুর" to R.string.city_kharagpur, // Bengali
    "खड़गपुर" to R.string.city_kharagpur, // Hindi
    "கரக்பூர்" to R.string.city_kharagpur, // Tamil
    "ଖଡ଼ଗପୁର" to R.string.city_kharagpur, // Odia
    "ఖరగ్‌పూర్" to R.string.city_kharagpur, // Telugu
    "खडगपूर" to R.string.city_kharagpur, // Marathi
    "ખડગપુર" to R.string.city_kharagpur, // Gujarati
    "ಖರಗ್‌ಪುರ್" to R.string.city_kharagpur, // Kannada
    "ഖരഗ്‌പൂർ" to R.string.city_kharagpur, // Malayalam
    "খড়গপুৰ" to R.string.city_kharagpur, // Assamese
    "ਖੜਗਪੁਰ" to R.string.city_kharagpur, // Punjabi

    // Kochi
    "Kochi" to R.string.city_kochi,
    "কোচি" to R.string.city_kochi, // Bengali
    "कोच्चि" to R.string.city_kochi, // Hindi
    "கொச்சி" to R.string.city_kochi, // Tamil
    "କୋଚି" to R.string.city_kochi, // Odia
    "కొచ్చి" to R.string.city_kochi, // Telugu
    "कोची" to R.string.city_kochi, // Marathi
    "કોચી" to R.string.city_kochi, // Gujarati
    "ಕೊಚ್ಚಿ" to R.string.city_kochi, // Kannada
    "കൊച്ചി" to R.string.city_kochi, // Malayalam
    "কোচি" to R.string.city_kochi, // Assamese
    "ਕੋਚੀ" to R.string.city_kochi, // Punjabi

    // Kohima
    "Kohima" to R.string.city_kohima,
    "কোহিমা" to R.string.city_kohima, // Bengali
    "कोहिमा" to R.string.city_kohima, // Hindi
    "கோஹிமா" to R.string.city_kohima, // Tamil
    "କୋହିମା" to R.string.city_kohima, // Odia
    "కోహిమా" to R.string.city_kohima, // Telugu
    "कोहिमा" to R.string.city_kohima, // Marathi
    "કોહિમા" to R.string.city_kohima, // Gujarati
    "ಕೋಹಿಮಾ" to R.string.city_kohima, // Kannada
    "കോഹിമ" to R.string.city_kohima, // Malayalam
    "কোহিমা" to R.string.city_kohima, // Assamese
    "ਕੋਹੀਮਾ" to R.string.city_kohima, // Punjabi

    // Kolkata
    "Kolkata" to R.string.city_kolkata,
    "কলকাতা" to R.string.city_kolkata, // Bengali
    "कोलकाता" to R.string.city_kolkata, // Hindi
    "கொல்கத்தா" to R.string.city_kolkata, // Tamil
    "କୋଲକାତା" to R.string.city_kolkata, // Odia
    "కోల్‌కతా" to R.string.city_kolkata, // Telugu
    "कोलकाता" to R.string.city_kolkata, // Marathi
    "કોલકાતા" to R.string.city_kolkata, // Gujarati
    "ಕೋಲ್ಕತ್ತಾ" to R.string.city_kolkata, // Kannada
    "കൊൽക്കത്ത" to R.string.city_kolkata, // Malayalam
    "কলকাতা" to R.string.city_kolkata, // Assamese
    "ਕੋਲਕਾਤਾ" to R.string.city_kolkata, // Punjabi

    // Leh
    "Leh" to R.string.city_leh,
    "লেহ" to R.string.city_leh, // Bengali
    "लेह" to R.string.city_leh, // Hindi
    "லே" to R.string.city_leh, // Tamil
    "ଲେହ" to R.string.city_leh, // Odia
    "లేహ్" to R.string.city_leh, // Telugu
    "लेह" to R.string.city_leh, // Marathi
    "લેહ" to R.string.city_leh, // Gujarati
    "ಲೇಹ್" to R.string.city_leh, // Kannada
    "ലേ" to R.string.city_leh, // Malayalam
    "লেহ" to R.string.city_leh, // Assamese
    "ਲੇਹ" to R.string.city_leh, // Punjabi

    // Ludhiana
    "Ludhiana" to R.string.city_ludhiana,
    "লুধিয়ানা" to R.string.city_ludhiana, // Bengali
    "लुधियाना" to R.string.city_ludhiana, // Hindi
    "லூதியானா" to R.string.city_ludhiana, // Tamil
    "ଲୁଧିଆନା" to R.string.city_ludhiana, // Odia
    "లూధియానా" to R.string.city_ludhiana, // Telugu
    "लुधियाना" to R.string.city_ludhiana, // Marathi
    "લુધિયાના" to R.string.city_ludhiana, // Gujarati
    "ಲುಧಿಯಾನಾ" to R.string.city_ludhiana, // Kannada
    "ലുധിയാന" to R.string.city_ludhiana, // Malayalam
    "লুধিয়ানা" to R.string.city_ludhiana, // Assamese
    "ਲੁਧਿਆਣਾ" to R.string.city_ludhiana, // Punjabi

    // Lucknow
    "Lucknow" to R.string.city_lucknow,
    "লখনউ" to R.string.city_lucknow, // Bengali
    "लखनऊ" to R.string.city_lucknow, // Hindi
    "லக்னோ" to R.string.city_lucknow, // Tamil
    "ଲଖନଉ" to R.string.city_lucknow, // Odia
    "లక్నో" to R.string.city_lucknow, // Telugu
    "लखनऊ" to R.string.city_lucknow, // Marathi
    "લખનઉ" to R.string.city_lucknow, // Gujarati
    "ಲಕ್ನೋ" to R.string.city_lucknow, // Kannada
    "ലഖ്‌നൗ" to R.string.city_lucknow, // Malayalam
    "লখনউ" to R.string.city_lucknow, // Assamese
    "ਲਖਨਊ" to R.string.city_lucknow, // Punjabi

    // Madurai
    "Madurai" to R.string.city_madurai,
    "মাদুরাই" to R.string.city_madurai, // Bengali
    "मदुरै" to R.string.city_madurai, // Hindi
    "மதுரை" to R.string.city_madurai, // Tamil
    "ମଦୁରାଇ" to R.string.city_madurai, // Odia
    "మదురై" to R.string.city_madurai, // Telugu
    "मदुराई" to R.string.city_madurai, // Marathi
    "મદુરાઈ" to R.string.city_madurai, // Gujarati
    "ಮದುರೈ" to R.string.city_madurai, // Kannada
    "മധുര" to R.string.city_madurai, // Malayalam
    "মাদুৰাই" to R.string.city_madurai, // Assamese
    "ਮਦੁਰਾਈ" to R.string.city_madurai, // Punjabi

    // Mumbai
    "Mumbai" to R.string.city_mumbai,
    "মুম্বাই" to R.string.city_mumbai, // Bengali
    "मुंबई" to R.string.city_mumbai, // Hindi
    "மும்பை" to R.string.city_mumbai, // Tamil
    "ମୁମ୍ବାଇ" to R.string.city_mumbai, // Odia
    "ముంబై" to R.string.city_mumbai, // Telugu
    "मुंबई" to R.string.city_mumbai, // Marathi
    "મુંબઈ" to R.string.city_mumbai, // Gujarati
    "ಮುಂಬೈ" to R.string.city_mumbai, // Kannada
    "മുംബൈ" to R.string.city_mumbai, // Malayalam
    "মুম্বাই" to R.string.city_mumbai, // Assamese
    "ਮੁੰਬਈ" to R.string.city_mumbai, // Punjabi

    // Mangaluru
    "Mangaluru" to R.string.city_mangaluru,
    "মঙ্গলুরু" to R.string.city_mangaluru, // Bengali
    "मंगलुरु" to R.string.city_mangaluru, // Hindi
    "மங்களூரு" to R.string.city_mangaluru, // Tamil
    "ମଙ୍ଗଲୁରୁ" to R.string.city_mangaluru, // Odia
    "మంగళూరు" to R.string.city_mangaluru, // Telugu
    "मंगलुरू" to R.string.city_mangaluru, // Marathi
    "મંગલુરુ" to R.string.city_mangaluru, // Gujarati
    "ಮಂಗಳೂರು" to R.string.city_mangaluru, // Kannada
    "മംഗലൂരു" to R.string.city_mangaluru, // Malayalam
    "মঙ্গলুৰু" to R.string.city_mangaluru, // Assamese
    "ਮੰਗਲੂਰੁ" to R.string.city_mangaluru, // Punjabi

    // Mysuru
    "Mysuru" to R.string.city_mysuru,
    "মাইসুরু" to R.string.city_mysuru, // Bengali
    "मैसूरु" to R.string.city_mysuru, // Hindi
    "மைசூரு" to R.string.city_mysuru, // Tamil
    "ମୈସୁରୁ" to R.string.city_mysuru, // Odia
    "మైసూరు" to R.string.city_mysuru, // Telugu
    "मैसूरु" to R.string.city_mysuru, // Marathi
    "મૈસુરુ" to R.string.city_mysuru, // Gujarati
    "ಮೈಸೂರು" to R.string.city_mysuru, // Kannada
    "മൈസൂരു" to R.string.city_mysuru, // Malayalam
    "মাইচুৰু" to R.string.city_mysuru, // Assamese
    "ਮੈਸੂਰੁ" to R.string.city_mysuru, // Punjabi

    // Nainital
    "Nainital" to R.string.city_nainital,
    "নৈনিতাল" to R.string.city_nainital, // Bengali
    "नैनीताल" to R.string.city_nainital, // Hindi
    "நைனிடால்" to R.string.city_nainital, // Tamil
    "ନୈନୀତାଲ" to R.string.city_nainital, // Odia
    "నైనిటాల్" to R.string.city_nainital, // Telugu
    "नैनीताल" to R.string.city_nainital, // Marathi
    "નૈનીતાલ" to R.string.city_nainital, // Gujarati
    "ನೈನಿಟಾಲ್" to R.string.city_nainital, // Kannada
    "നൈനിറ്റാൽ" to R.string.city_nainital, // Malayalam
    "নৈনিতাল" to R.string.city_nainital, // Assamese
    "ਨੈਨੀਤਾਲ" to R.string.city_nainital, // Punjabi

    // Nagpur
    "Nagpur" to R.string.city_nagpur,
    "নাগপুর" to R.string.city_nagpur, // Bengali
    "नागपुर" to R.string.city_nagpur, // Hindi
    "நாக்பூர்" to R.string.city_nagpur, // Tamil
    "ନାଗପୁର" to R.string.city_nagpur, // Odia
    "నాగ్‌పూర్" to R.string.city_nagpur, // Telugu
    "नागपूर" to R.string.city_nagpur, // Marathi
    "નાગપુર" to R.string.city_nagpur, // Gujarati
    "ನಾಗಪುರ್" to R.string.city_nagpur, // Kannada
    "നാഗ്‌പൂർ" to R.string.city_nagpur, // Malayalam
    "নাগপুৰ" to R.string.city_nagpur, // Assamese
    "ਨਾਗਪੁਰ" to R.string.city_nagpur, // Punjabi

    // Namchi
    "Namchi" to R.string.city_namchi,
    "নামচি" to R.string.city_namchi, // Bengali
    "नामची" to R.string.city_namchi, // Hindi
    "நாம்சி" to R.string.city_namchi, // Tamil
    "ନାମଚି" to R.string.city_namchi, // Odia
    "నామ్చి" to R.string.city_namchi, // Telugu
    "नामची" to R.string.city_namchi, // Marathi
    "નામચી" to R.string.city_namchi, // Gujarati
    "ನಾಮ್ಚಿ" to R.string.city_namchi, // Kannada
    "നാംചി" to R.string.city_namchi, // Malayalam
    "নামচি" to R.string.city_namchi, // Assamese
    "ਨਾਮਚੀ" to R.string.city_namchi, // Punjabi

    // Navi Mumbai
    "Navi Mumbai" to R.string.city_navi_mumbai,
    "নাভি মুম্বাই" to R.string.city_navi_mumbai, // Bengali
    "नवी मुंबई" to R.string.city_navi_mumbai, // Hindi
    "நவி மும்பை" to R.string.city_navi_mumbai, // Tamil
    "ନାଭି ମୁମ୍ବାଇ" to R.string.city_navi_mumbai, // Odia
    "నవీ ముంబై" to R.string.city_navi_mumbai, // Telugu
    "नवी मुंबई" to R.string.city_navi_mumbai, // Marathi
    "નવી મુંબઈ" to R.string.city_navi_mumbai, // Gujarati
    "ನವೀ ಮುಂಬೈ" to R.string.city_navi_mumbai, // Kannada
    "നവി മുംബൈ" to R.string.city_navi_mumbai, // Malayalam
    "নাভি মুম্বাই" to R.string.city_navi_mumbai, // Assamese
    "ਨਵੀਂ ਮੁੰਬਈ" to R.string.city_navi_mumbai, // Punjabi

    // NCT of Delhi
    "NCT of Delhi" to R.string.city_nct_of_delhi,
    "এনসিটি অফ দিল্লি" to R.string.city_nct_of_delhi, // Bengali
    "दिल्ली एनसीटी" to R.string.city_nct_of_delhi, // Hindi
    "டெல்லி என்.சி.டி" to R.string.city_nct_of_delhi, // Tamil
    "ଦିଲ୍ଲୀ ଏନସିଟି" to R.string.city_nct_of_delhi, // Odia
    "ఢిల్లీ ఎన్‌సిటి" to R.string.city_nct_of_delhi, // Telugu
    "दिल्ली एनसीटी" to R.string.city_nct_of_delhi, // Marathi
    "દિલ્લી એનસીટી" to R.string.city_nct_of_delhi, // Gujarati
    "ದೆಹಲಿ ಎನ್‌ಸಿಟಿ" to R.string.city_nct_of_delhi, // Kannada
    "ഡൽഹി എൻ.സി.ടി" to R.string.city_nct_of_delhi, // Malayalam
    "এনচিটি অফ দিল্লি" to R.string.city_nct_of_delhi, // Assamese
    "ਦਿੱਲੀ ਐਨਸੀਟੀ" to R.string.city_nct_of_delhi, // Punjabi

    // Noida
    "Noida" to R.string.city_noida,
    "নয়ডা" to R.string.city_noida, // Bengali
    "नोएडा" to R.string.city_noida, // Hindi
    "நொய்டா" to R.string.city_noida, // Tamil
    "ନୋଏଡ଼ା" to R.string.city_noida, // Odia
    "నోయిడా" to R.string.city_noida, // Telugu
    "नोएडा" to R.string.city_noida, // Marathi
    "નોઈડા" to R.string.city_noida, // Gujarati
    "ನೋಯ್ಡಾ" to R.string.city_noida, // Kannada
    "നോയിഡ" to R.string.city_noida, // Malayalam
    "নয়ডা" to R.string.city_noida, // Assamese
    "ਨੋਇਡਾ" to R.string.city_noida, // Punjabi

    // Panaji
    "Panaji" to R.string.city_panaji,
    "পানাজি" to R.string.city_panaji, // Bengali
    "पणजी" to R.string.city_panaji, // Hindi
    "பணஜி" to R.string.city_panaji, // Tamil
    "ପାନାଜି" to R.string.city_panaji, // Odia
    "పణజి" to R.string.city_panaji, // Telugu
    "पणजी" to R.string.city_panaji, // Marathi
    "પણજી" to R.string.city_panaji, // Gujarati
    "ಪಣಜಿ" to R.string.city_panaji, // Kannada
    "പനാജി" to R.string.city_panaji, // Malayalam
    "পানাজি" to R.string.city_panaji, // Assamese
    "ਪਣਜੀ" to R.string.city_panaji, // Punjabi

    // Pasighat
    "Pasighat" to R.string.city_pasighat,
    "পাসিঘাট" to R.string.city_pasighat, // Bengali
    "पासीघाट" to R.string.city_pasighat, // Hindi
    "பாசிகாட்" to R.string.city_pasighat, // Tamil
    "ପାସିଘାଟ" to R.string.city_pasighat, // Odia
    "పాసిఘాట్" to R.string.city_pasighat, // Telugu
    "पासीघाट" to R.string.city_pasighat, // Marathi
    "પાસીઘાટ" to R.string.city_pasighat, // Gujarati
    "ಪಾಸಿಘಾಟ್" to R.string.city_pasighat, // Kannada
    "പാസിഘാട്" to R.string.city_pasighat, // Malayalam
    "পাচিঘাট" to R.string.city_pasighat, // Assamese
    "ਪਾਸੀਘਾਟ" to R.string.city_pasighat, // Punjabi

    // Patna
    "Patna" to R.string.city_patna,
    "পাটনা" to R.string.city_patna, // Bengali
    "पटना" to R.string.city_patna, // Hindi
    "பாட்னா" to R.string.city_patna, // Tamil
    "ପାଟନା" to R.string.city_patna, // Odia
    "పట్నా" to R.string.city_patna, // Telugu
    "पटणा" to R.string.city_patna, // Marathi
    "પટના" to R.string.city_patna, // Gujarati
    "ಪಟ್ನಾ" to R.string.city_patna, // Kannada
    "പട്ന" to R.string.city_patna, // Malayalam
    "পাটনা" to R.string.city_patna, // Assamese
    "ਪਟਨਾ" to R.string.city_patna, // Punjabi

    // Prayagraj
    "Prayagraj" to R.string.city_prayagraj,
    "প্রয়াগরাজ" to R.string.city_prayagraj, // Bengali
    "प्रयागराज" to R.string.city_prayagraj, // Hindi
    "பிரயாக்ராஜ்" to R.string.city_prayagraj, // Tamil
    "ପ୍ରୟାଗରାଜ" to R.string.city_prayagraj, // Odia
    "ప్రయాగ్‌రాజ్" to R.string.city_prayagraj, // Telugu
    "प्रयागराज" to R.string.city_prayagraj, // Marathi
    "પ્રયાગરાજ" to R.string.city_prayagraj, // Gujarati
    "ಪ್ರಯಾಗರಾಜ್" to R.string.city_prayagraj, // Kannada
    "പ്രയാഗ്‌രാജ്" to R.string.city_prayagraj, // Malayalam
    "প্ৰয়াগৰাজ" to R.string.city_prayagraj, // Assamese
    "ਪ੍ਰਯਾਗਰਾਜ" to R.string.city_prayagraj, // Punjabi

    // Pune
    "Pune" to R.string.city_pune,
    "পুনে" to R.string.city_pune, // Bengali
    "पुणे" to R.string.city_pune, // Hindi
    "புனே" to R.string.city_pune, // Tamil
    "ପୁନେ" to R.string.city_pune, // Odia
    "పూనే" to R.string.city_pune, // Telugu
    "पुणे" to R.string.city_pune, // Marathi
    "પુણે" to R.string.city_pune, // Gujarati
    "ಪುಣೆ" to R.string.city_pune, // Kannada
    "പൂനെ" to R.string.city_pune, // Malayalam
    "পুনে" to R.string.city_pune, // Assamese
    "ਪੁਣੇ" to R.string.city_pune, // Punjabi

    // Port Blair
    "Port Blair" to R.string.city_port_blair,
    "পোর্ট ব্লেয়ার" to R.string.city_port_blair, // Bengali
    "पोर्ट ब्लेयर" to R.string.city_port_blair, // Hindi
    "போர்ட் பிளேர்" to R.string.city_port_blair, // Tamil
    "ପୋର୍ଟ ବ୍ଲେୟାର" to R.string.city_port_blair, // Odia
    "పోర్ట్ బ్లెయిర్" to R.string.city_port_blair, // Telugu
    "पोर्ट ब्लेअर" to R.string.city_port_blair, // Marathi
    "પોર્ટ બ્લેર" to R.string.city_port_blair, // Gujarati
    "ಪೋರ್ಟ್ ಬ್ಲೇರ್" to R.string.city_port_blair, // Kannada
    "പോർട്ട് ബ്ലെയർ" to R.string.city_port_blair, // Malayalam
    "পোৰ্ট ব্লেয়াৰ" to R.string.city_port_blair, // Assamese
    "ਪੋਰਟ ਬਲੇਅਰ" to R.string.city_port_blair, // Punjabi

    // Puducherry
    "Puducherry" to R.string.city_puducherry,
    "পুদুচেরি" to R.string.city_puducherry, // Bengali
    "पुडुचेरी" to R.string.city_puducherry, // Hindi
    "புதுச்சேரி" to R.string.city_puducherry, // Tamil
    "ପୁଦୁଚେରୀ" to R.string.city_puducherry, // Odia
    "పుదుచ్చేరి" to R.string.city_puducherry, // Telugu
    "पुडुचेरी" to R.string.city_puducherry, // Marathi
    "પુડુચેરી" to R.string.city_puducherry, // Gujarati
    "ಪುದುಚೇರಿ" to R.string.city_puducherry, // Kannada
    "പുതുച്ചേരി" to R.string.city_puducherry, // Malayalam
    "পুদুচেৰী" to R.string.city_puducherry, // Assamese
    "ਪੁਡੁਚੇਰੀ" to R.string.city_puducherry, // Punjabi

    // Raipur
    "Raipur" to R.string.city_raipur,
    "রায়পুর" to R.string.city_raipur, // Bengali
    "रायपुर" to R.string.city_raipur, // Hindi
    "ராய்ப்பூர்" to R.string.city_raipur, // Tamil
    "ରାୟପୁର" to R.string.city_raipur, // Odia
    "రాయ్‌పూర్" to R.string.city_raipur, // Telugu
    "रायपूर" to R.string.city_raipur, // Marathi
    "રાયપુર" to R.string.city_raipur, // Gujarati
    "ರಾಯ್‌ಪುರ್" to R.string.city_raipur, // Kannada
    "റായ്‌പൂർ" to R.string.city_raipur, // Malayalam
    "ৰায়পুৰ" to R.string.city_raipur, // Assamese
    "ਰਾਏਪੁਰ" to R.string.city_raipur, // Punjabi

    // Ranchi
    "Ranchi" to R.string.city_ranchi,
    "রাঁচি" to R.string.city_ranchi, // Bengali
    "रांची" to R.string.city_ranchi, // Hindi
    "ராஞ்சி" to R.string.city_ranchi, // Tamil
    "ରାଞ୍ଚି" to R.string.city_ranchi, // Odia
    "రాంచీ" to R.string.city_ranchi, // Telugu
    "रांची" to R.string.city_ranchi, // Marathi
    "રાંચી" to R.string.city_ranchi, // Gujarati
    "ರಾಂಚಿ" to R.string.city_ranchi, // Kannada
    "റാഞ്ചി" to R.string.city_ranchi, // Malayalam
    "ৰাঁচি" to R.string.city_ranchi, // Assamese
    "ਰਾਂਚੀ" to R.string.city_ranchi, // Punjabi

    // Rourkela
    "Rourkela" to R.string.city_rourkela,
    "রাউরকেলা" to R.string.city_rourkela, // Bengali
    "राउरकेला" to R.string.city_rourkela, // Hindi
    "ரூர்கேலா" to R.string.city_rourkela, // Tamil
    "ରାଉରକେଲା" to R.string.city_rourkela, // Odia
    "రౌర్కెలా" to R.string.city_rourkela, // Telugu
    "राउरकेला" to R.string.city_rourkela, // Marathi
    "રાઉરકેલા" to R.string.city_rourkela, // Gujarati
    "ರೌರ್ಕೇಲಾ" to R.string.city_rourkela, // Kannada
    "റൂർക്കേല" to R.string.city_rourkela, // Malayalam
    "ৰাউৰকেলা" to R.string.city_rourkela, // Assamese
    "ਰੌਰਕੇਲਾ" to R.string.city_rourkela, // Punjabi

    // Rohtak
    "Rohtak" to R.string.city_rohtak,
    "রোহতক" to R.string.city_rohtak, // Bengali
    "रोहतक" to R.string.city_rohtak, // Hindi
    "ரோஹ்டக்" to R.string.city_rohtak, // Tamil
    "ରୋହତକ" to R.string.city_rohtak, // Odia
    "రోహ్తక్" to R.string.city_rohtak, // Telugu
    "रोहतक" to R.string.city_rohtak, // Marathi
    "રોહતક" to R.string.city_rohtak, // Gujarati
    "ರೋಹ್ತಕ್" to R.string.city_rohtak, // Kannada
    "റോഹ്‌തക്" to R.string.city_rohtak, // Malayalam
    "ৰোহতক" to R.string.city_rohtak, // Assamese
    "ਰੋਹਤਕ" to R.string.city_rohtak, // Punjabi

    // Shillong
    "Shillong" to R.string.city_shillong,
    "শিলং" to R.string.city_shillong, // Bengali
    "शिलांग" to R.string.city_shillong, // Hindi
    "ஷில்லாங்" to R.string.city_shillong, // Tamil
    "ଶିଲଂ" to R.string.city_shillong, // Odia
    "షిల్లాంగ్" to R.string.city_shillong, // Telugu
    "शिलॉंग" to R.string.city_shillong, // Marathi
    "શિલોંગ" to R.string.city_shillong, // Gujarati
    "ಶಿಲ್ಲಾಂಗ್" to R.string.city_shillong, // Kannada
    "ഷില്ലോങ്" to R.string.city_shillong, // Malayalam
    "শিলং" to R.string.city_shillong, // Assamese
    "ਸ਼ਿਲੌਂਗ" to R.string.city_shillong, // Punjabi

    // Shimla
    "Shimla" to R.string.city_shimla,
    "শিমলা" to R.string.city_shimla, // Bengali
    "शिमला" to R.string.city_shimla, // Hindi
    "ஷிம்லா" to R.string.city_shimla, // Tamil
    "ଶିମଲା" to R.string.city_shimla, // Odia
    "షిమ్లా" to R.string.city_shimla, // Telugu
    "शिमला" to R.string.city_shimla, // Marathi
    "શિમલા" to R.string.city_shimla, // Gujarati
    "ಶಿಮ್ಲಾ" to R.string.city_shimla, // Kannada
    "ഷിംല" to R.string.city_shimla, // Malayalam
    "শিমলা" to R.string.city_shimla, // Assamese
    "ਸ਼ਿਮਲਾ" to R.string.city_shimla, // Punjabi

    // Silchar
    "Silchar" to R.string.city_silchar,
    "শিলচর" to R.string.city_silchar, // Bengali
    "सिलचर" to R.string.city_silchar, // Hindi
    "சில்சர்" to R.string.city_silchar, // Tamil
    "ଶିଲଚର" to R.string.city_silchar, // Odia
    "సిల్చార్" to R.string.city_silchar, // Telugu
    "सिलचर" to R.string.city_silchar, // Marathi
    "સિલચર" to R.string.city_silchar, // Gujarati
    "ಸಿಲ್ಚಾರ್" to R.string.city_silchar, // Kannada
    "സിൽചാർ" to R.string.city_silchar, // Malayalam
    "শিলচৰ" to R.string.city_silchar, // Assamese
    "ਸਿਲਚਰ" to R.string.city_silchar, // Punjabi

    // Siliguri
    "Siliguri" to R.string.city_siliguri,
    "শিলিগুড়ি" to R.string.city_siliguri, // Bengali
    "शिलिगुड़ी" to R.string.city_siliguri, // Hindi
    "சிலிகுரி" to R.string.city_siliguri, // Tamil
    "ଶିଲିଗୁଡ଼ି" to R.string.city_siliguri, // Odia
    "సిలిగురి" to R.string.city_siliguri, // Telugu
    "शिलीगुडी" to R.string.city_siliguri, // Marathi
    "સિલિગુડી" to R.string.city_siliguri, // Gujarati
    "ಸಿಲಿಗುರಿ" to R.string.city_siliguri, // Kannada
    "സിലിഗുരി" to R.string.city_siliguri, // Malayalam
    "শিলিগুৰি" to R.string.city_siliguri, // Assamese
    "ਸਿਲੀਗੁੜੀ" to R.string.city_siliguri, // Punjabi

    // Sonipat
    "Sonipat" to R.string.city_sonipat,
    "সোনিপত" to R.string.city_sonipat, // Bengali
    "सोनीपत" to R.string.city_sonipat, // Hindi
    "சோனிபத்" to R.string.city_sonipat, // Tamil
    "ସୋନୀପତ" to R.string.city_sonipat, // Odia
    "సోనిపట్" to R.string.city_sonipat, // Telugu
    "सोनीपत" to R.string.city_sonipat, // Marathi
    "સોનીપત" to R.string.city_sonipat, // Gujarati
    "ಸೋನಿಪಟ್" to R.string.city_sonipat, // Kannada
    "സോനിപത്" to R.string.city_sonipat, // Malayalam
    "ছোনিপত" to R.string.city_sonipat, // Assamese
    "ਸੋਨੀਪਤ" to R.string.city_sonipat, // Punjabi

    // Surat
    "Surat" to R.string.city_surat,
    "সুরাট" to R.string.city_surat, // Bengali
    "सूरत" to R.string.city_surat, // Hindi
    "சூரத்" to R.string.city_surat, // Tamil
    "ସୁରଟ" to R.string.city_surat, // Odia
    "సూరత్" to R.string.city_surat, // Telugu
    "सुरत" to R.string.city_surat, // Marathi
    "સુરત" to R.string.city_surat, // Gujarati
    "ಸೂರತ್" to R.string.city_surat, // Kannada
    "സൂറത്ത്" to R.string.city_surat, // Malayalam
    "সুৰাট" to R.string.city_surat, // Assamese
    "ਸੂਰਤ" to R.string.city_surat, // Punjabi

    // Secunderabad
    "Secunderabad" to R.string.city_secunderabad,
    "সেকেন্দ্রাবাদ" to R.string.city_secunderabad, // Bengali
    "सिकंदराबाद" to R.string.city_secunderabad, // Hindi
    "சிக்கந்தராபாத்" to R.string.city_secunderabad, // Tamil
    "ସେକେନ୍ଦ୍ରାବାଦ" to R.string.city_secunderabad, // Odia
    "సికింద్రాబాద్" to R.string.city_secunderabad, // Telugu
    "सिकंदराबाद" to R.string.city_secunderabad, // Marathi
    "સિકંદરાબાદ" to R.string.city_secunderabad, // Gujarati
    "ಸಿಕಂದರಾಬಾದ್" to R.string.city_secunderabad, // Kannada
    "സെക്കന്തരാബാദ്" to R.string.city_secunderabad, // Malayalam
    "চেকেণ্ডাৰাবাদ" to R.string.city_secunderabad, // Assamese
    "ਸਿਕੰਦਰਾਬਾਦ" to R.string.city_secunderabad, // Punjabi

    // Tawang
    "Tawang" to R.string.city_tawang,
    "তাওয়াং" to R.string.city_tawang, // Bengali
    "तवांग" to R.string.city_tawang, // Hindi
    "தவாங்" to R.string.city_tawang, // Tamil
    "ତାୱାଙ୍ଗ" to R.string.city_tawang, // Odia
    "తవాంగ్" to R.string.city_tawang, // Telugu
    "तवांग" to R.string.city_tawang, // Marathi
    "તવાંગ" to R.string.city_tawang, // Gujarati
    "ತವಾಂಗ್" to R.string.city_tawang, // Kannada
    "തവാങ്" to R.string.city_tawang, // Malayalam
    "তাৱাং" to R.string.city_tawang, // Assamese
    "ਤਵੰਗ" to R.string.city_tawang, // Punjabi

    // Thane
    "Thane" to R.string.city_thane,
    "থানে" to R.string.city_thane, // Bengali
    "ठाणे" to R.string.city_thane, // Hindi
    "தானே" to R.string.city_thane, // Tamil
    "ଥାନେ" to R.string.city_thane, // Odia
    "థానే" to R.string.city_thane, // Telugu
    "ठाणे" to R.string.city_thane, // Marathi
    "થાણે" to R.string.city_thane, // Gujarati
    "ಥಾಣೆ" to R.string.city_thane, // Kannada
    "താനെ" to R.string.city_thane, // Malayalam
    "থানে" to R.string.city_thane, // Assamese
    "ਠਾਣੇ" to R.string.city_thane, // Punjabi

    // Thiruvananthapuram
    "Thiruvananthapuram" to R.string.city_thiruvananthapuram,
    "তিরুবনন্তপুরম" to R.string.city_thiruvananthapuram, // Bengali
    "तिरुवनंतपुरम" to R.string.city_thiruvananthapuram, // Hindi
    "திருவனந்தபுரம்" to R.string.city_thiruvananthapuram, // Tamil
    "ତିରୁବନନ୍ତପୁରମ" to R.string.city_thiruvananthapuram, // Odia
    "తిరువనంతపురం" to R.string.city_thiruvananthapuram, // Telugu
    "तिरुअनंतपुरम" to R.string.city_thiruvananthapuram, // Marathi
    "તિરુવનંતપુરમ" to R.string.city_thiruvananthapuram, // Gujarati
    "ತಿರುವನಂತಪುರಂ" to R.string.city_thiruvananthapuram, // Kannada
    "തിരുവനന്തപുരം" to R.string.city_thiruvananthapuram, // Malayalam
    "তিৰুৱনন্তপুৰম" to R.string.city_thiruvananthapuram, // Assamese
    "ਤਿਰੂਵਨੰਤਪੁਰਮ" to R.string.city_thiruvananthapuram, // Punjabi

    // Udaipur
    "Udaipur" to R.string.city_udaipur,
    "উদয়পুর" to R.string.city_udaipur, // Bengali
    "उदयपुर" to R.string.city_udaipur, // Hindi
    "உதய்ப்பூர்" to R.string.city_udaipur, // Tamil
    "ଉଦୟପୁର" to R.string.city_udaipur, // Odia
    "ఉదయ్‌పూర్" to R.string.city_udaipur, // Telugu
    "उदयपूर" to R.string.city_udaipur, // Marathi
    "ઉદયપુર" to R.string.city_udaipur, // Gujarati
    "ಉದಯ್‌ಪುರ್" to R.string.city_udaipur, // Kannada
    "ഉദയ്‌പൂർ" to R.string.city_udaipur, // Malayalam
    "উদয়পুৰ" to R.string.city_udaipur, // Assamese
    "ਉਦੈਪੁਰ" to R.string.city_udaipur, // Punjabi

    // Vadodara
    "Vadodara" to R.string.city_vadodara,
    "বড়োদরা" to R.string.city_vadodara, // Bengali
    "वडोदरा" to R.string.city_vadodara, // Hindi
    "வடோதரா" to R.string.city_vadodara, // Tamil
    "ବଡ଼ୋଦରା" to R.string.city_vadodara, // Odia
    "వడోదర" to R.string.city_vadodara, // Telugu
    "वडोदरा" to R.string.city_vadodara, // Marathi
    "વડોદરા" to R.string.city_vadodara, // Gujarati
    "ವಡೋದರ" to R.string.city_vadodara, // Kannada
    "വഡോദര" to R.string.city_vadodara, // Malayalam
    "বড়োদৰা" to R.string.city_vadodara, // Assamese
    "ਵਡੋਦਰਾ" to R.string.city_vadodara, // Punjabi

    // Varanasi
    "Varanasi" to R.string.city_varanasi,
    "বারাণসী" to R.string.city_varanasi, // Bengali
    "वाराणसी" to R.string.city_varanasi, // Hindi
    "வாரணாசி" to R.string.city_varanasi, // Tamil
    "ବାରାଣସୀ" to R.string.city_varanasi, // Odia
    "వారణాసి" to R.string.city_varanasi, // Telugu
    "वाराणसी" to R.string.city_varanasi, // Marathi
    "વારાણસી" to R.string.city_varanasi, // Gujarati
    "ವಾರಣಾಸಿ" to R.string.city_varanasi, // Kannada
    "വാരണാസി" to R.string.city_varanasi, // Malayalam
    "বাৰাণসী" to R.string.city_varanasi, // Assamese
    "ਵਾਰਾਣਸੀ" to R.string.city_varanasi, // Punjabi

    // Vellore
    "Vellore" to R.string.city_vellore,
    "ভেলোর" to R.string.city_vellore, // Bengali
    "वेल्लोर" to R.string.city_vellore, // Hindi
    "வேலூர்" to R.string.city_vellore, // Tamil
    "ଭେଲୋର" to R.string.city_vellore, // Odia
    "వేలూర్" to R.string.city_vellore, // Telugu
    "वेल्लोर" to R.string.city_vellore, // Marathi
    "વેલ્લોર" to R.string.city_vellore, // Gujarati
    "ವೆಲ್ಲೂರ್" to R.string.city_vellore, // Kannada
    "വെല്ലൂർ" to R.string.city_vellore, // Malayalam
    "ভেলোৰ" to R.string.city_vellore, // Assamese
    "ਵੇਲੋਰ" to R.string.city_vellore, // Punjabi

    // Vijayawada
    "Vijayawada" to R.string.city_vijayawada,
    "Vijayawada" to R.string.city_vijayawada, // Bengali (fallback)
    "विजयवाड़ा" to R.string.city_vijayawada, // Hindi
    "Vijayawada" to R.string.city_vijayawada, // Tamil (fallback)
    "Vijayawada" to R.string.city_vijayawada, // Odia (fallback)
    "విజయవాడ" to R.string.city_vijayawada, // Telugu
    "Vijayawada" to R.string.city_vijayawada, // Marathi (fallback)
    "Vijayawada" to R.string.city_vijayawada, // Gujarati (fallback)
    "Vijayawada" to R.string.city_vijayawada, // Kannada (fallback)
    "Vijayawada" to R.string.city_vijayawada, // Malayalam (fallback)
    "Vijayawada" to R.string.city_vijayawada, // Assamese (fallback)
    "Vijayawada" to R.string.city_vijayawada, // Punjabi (fallback)

    // Visakhapatnam
    "Visakhapatnam" to R.string.city_visakhapatnam,
    "Visakhapatnam" to R.string.city_visakhapatnam, // Bengali (fallback)
    "विशाखापट्टनम" to R.string.city_visakhapatnam, // Hindi
    "Visakhapatnam" to R.string.city_visakhapatnam, // Tamil (fallback)
    "Visakhapatnam" to R.string.city_visakhapatnam, // Odia (fallback)
    "విశాఖపట్నం" to R.string.city_visakhapatnam, // Telugu
    "Visakhapatnam" to R.string.city_visakhapatnam, // Marathi (fallback)
    "Visakhapatnam" to R.string.city_visakhapatnam, // Gujarati (fallback)
    "Visakhapatnam" to R.string.city_visakhapatnam, // Kannada (fallback)
    "Visakhapatnam" to R.string.city_visakhapatnam, // Malayalam (fallback)
    "Visakhapatnam" to R.string.city_visakhapatnam, // Assamese (fallback)
    "Visakhapatnam" to R.string.city_visakhapatnam, // Punjabi (fallback)

    // Warangal
    "Warangal" to R.string.city_warangal,
    "Warangal" to R.string.city_warangal, // Bengali (fallback)
    "वारंगल" to R.string.city_warangal, // Hindi
    "Warangal" to R.string.city_warangal, // Tamil (fallback)
    "Warangal" to R.string.city_warangal, // Odia (fallback)
    "వరంగల్" to R.string.city_warangal, // Telugu
    "Warangal" to R.string.city_warangal, // Marathi (fallback)
    "Warangal" to R.string.city_warangal, // Gujarati (fallback)
    "Warangal" to R.string.city_warangal, // Kannada (fallback)
    "Warangal" to R.string.city_warangal, // Malayalam (fallback)
    "Warangal" to R.string.city_warangal, // Assamese (fallback)
    "Warangal" to R.string.city_warangal, // Punjabi (fallback)

    // Other
    "Other" to R.string.city_other,
    "অন্যান্য" to R.string.city_other, // Bengali
    "अन्य" to R.string.city_other, // Hindi
    "மற்றவை" to R.string.city_other, // Tamil
    "ଅନ୍ୟାନ୍ୟ" to R.string.city_other, // Odia
    "ఇతర" to R.string.city_other, // Telugu
    "इतर" to R.string.city_other, // Marathi
    "અન્ય" to R.string.city_other, // Gujarati
    "ಇತರೆ" to R.string.city_other, // Kannada
    "മറ്റുള്ളവ" to R.string.city_other, // Malayalam
    "অন্যান্য" to R.string.city_other, // Assamese
    "ਹੋਰ" to R.string.city_other // Punjabi
)

// ─── Preferences ──────────────────────────────────

val lookingForNameToRes = mapOf(
    // Romance
    "Romance" to R.string.looking_for_romance,
    "রোমান্স" to R.string.looking_for_romance, // Bengali
    "रोमांस" to R.string.looking_for_romance, // Hindi
    "காதல்" to R.string.looking_for_romance, // Tamil
    "ରୋମାନ୍ସ" to R.string.looking_for_romance, // Odia
    "రొమాన్స్" to R.string.looking_for_romance, // Telugu
    "रोमान्स" to R.string.looking_for_romance, // Marathi
    "રોમાન્સ" to R.string.looking_for_romance, // Gujarati
    "ರೊಮಾನ್ಸ್" to R.string.looking_for_romance, // Kannada
    "റൊമാൻസ്" to R.string.looking_for_romance, // Malayalam
    "ৰোমান্স" to R.string.looking_for_romance, // Assamese
    "ਰੋਮਾਂਸ" to R.string.looking_for_romance, // Punjabi

    // Connection
    "Connection" to R.string.looking_for_connection,
    "Conexión" to R.string.looking_for_connection,
    "সংযোগ" to R.string.looking_for_connection, // Bengali
    "कनेक्शन" to R.string.looking_for_connection, // Hindi
    "இணைப்பு" to R.string.looking_for_connection, // Tamil
    "ସଂଯୋଗ" to R.string.looking_for_connection, // Odia
    "కనెక్షన్" to R.string.looking_for_connection, // Telugu
    "जोडणी" to R.string.looking_for_connection, // Marathi
    "જોડાણ" to R.string.looking_for_connection, // Gujarati
    "ಸಂಪರ್ಕ" to R.string.looking_for_connection, // Kannada
    "ബന്ധം" to R.string.looking_for_connection, // Malayalam
    "সংযোগ" to R.string.looking_for_connection, // Assamese
    "ਕਨੈਕਸ਼ਨ" to R.string.looking_for_connection, // Punjabi

    // Partner
    "Partner" to R.string.looking_for_partner,
    "Pareja" to R.string.looking_for_partner,
    "সঙ্গী" to R.string.looking_for_partner, // Bengali
    "साथी" to R.string.looking_for_partner, // Hindi
    "துணை" to R.string.looking_for_partner, // Tamil
    "ସାଙ୍ଗୀ" to R.string.looking_for_partner, // Odia
    "భాగస్వామి" to R.string.looking_for_partner, // Telugu
    "साथीदार" to R.string.looking_for_partner, // Marathi
    "સાથી" to R.string.looking_for_partner, // Gujarati
    "ಸಂಗಾತಿ" to R.string.looking_for_partner, // Kannada
    "പങ്കാളി" to R.string.looking_for_partner, // Malayalam
    "সঙ্গী" to R.string.looking_for_partner, // Assamese
    "ਸਾਥੀ" to R.string.looking_for_partner, // Punjabi
    "Casual" to R.string.looking_for_casual,
    "Citas" to R.string.looking_for_dating,
    "Relación exclusiva" to R.string.looking_for_exclusive,
    // Marriage
    "Marriage" to R.string.looking_for_marriage,
    "Matrimonio" to R.string.looking_for_marriage,
    "বিবাহ" to R.string.looking_for_marriage, // Bengali
    "विवाह" to R.string.looking_for_marriage, // Hindi
    "திருமணம்" to R.string.looking_for_marriage, // Tamil
    "ବିବାହ" to R.string.looking_for_marriage, // Odia
    "వివాహం" to R.string.looking_for_marriage, // Telugu
    "विवाह" to R.string.looking_for_marriage, // Marathi
    "લગ્ન" to R.string.looking_for_marriage, // Gujarati
    "ವಿವಾಹ" to R.string.looking_for_marriage, // Kannada
    "വിവാഹം" to R.string.looking_for_marriage, // Malayalam
    "বিবাহ" to R.string.looking_for_marriage, // Assamese
    "ਵਿਆਹ" to R.string.looking_for_marriage, // Punjabi
    "Long term" to R.string.looking_for_long_term,
    "Relación a largo plazo" to R.string.looking_for_long_term,
    "Corto plazo: abierto a largo" to R.string.looking_for_short_to_long,
    "Casual" to R.string.looking_for_casual,
    "Dating" to R.string.looking_for_dating,
    "Exclusive" to R.string.looking_for_exclusive
)

val loveLanguageNameToRes = mapOf(

    "Palabras de afirmación" to R.string.love_language_option_words_of_affirmation,
    "Actos de servicio" to R.string.love_language_option_acts_of_service,
    "Recibir regalos" to R.string.love_language_option_receiving_gifts,
    "Tiempo de calidad" to R.string.love_language_option_quality_time,
    "Contacto físico" to R.string.love_language_option_physical_touch,
    // Words of Affirmation
    "Words of Affirmation" to R.string.love_language_option_words_of_affirmation,
    "প্রশংসার শব্দ" to R.string.love_language_option_words_of_affirmation, // Bengali
    "प्रशंसा के शब्द" to R.string.love_language_option_words_of_affirmation, // Hindi
    "புகழுரைகள்" to R.string.love_language_option_words_of_affirmation, // Tamil
    "ପ୍ରଶଂସାର ଶବ୍ଦ" to R.string.love_language_option_words_of_affirmation, // Odia
    "ప్రశంసల మాటలు" to R.string.love_language_option_words_of_affirmation, // Telugu
    "प्रशंसेचे शब्द" to R.string.love_language_option_words_of_affirmation, // Marathi
    "પ્રશંસાના શબ્દો" to R.string.love_language_option_words_of_affirmation, // Gujarati
    "ಪ್ರಶಂಸೆಯ ಮಾತುಗಳು" to R.string.love_language_option_words_of_affirmation, // Kannada
    "പ്രശംസാവാക്കുകൾ" to R.string.love_language_option_words_of_affirmation, // Malayalam
    "প্ৰশংসাৰ শব্দ" to R.string.love_language_option_words_of_affirmation, // Assamese
    "ਤਾਰੀਫ਼ ਦੇ ਸ਼ਬਦ" to R.string.love_language_option_words_of_affirmation, // Punjabi

    // Acts of Service
    "Acts of Service" to R.string.love_language_option_acts_of_service,
    "পরিষেবার কাজ" to R.string.love_language_option_acts_of_service, // Bengali
    "सेवा कार्य" to R.string.love_language_option_acts_of_service, // Hindi
    "சேவைகள்" to R.string.love_language_option_acts_of_service, // Tamil
    "ସେବା କାର୍ଯ୍ୟ" to R.string.love_language_option_acts_of_service, // Odia
    "సేవా కార్యకలాపాలు" to R.string.love_language_option_acts_of_service, // Telugu
    "सेवेची कृती" to R.string.love_language_option_acts_of_service, // Marathi
    "સેવાનાં કાર્યો" to R.string.love_language_option_acts_of_service, // Gujarati
    "ಸೇವೆಯ ಕಾರ್ಯಗಳು" to R.string.love_language_option_acts_of_service, // Kannada
    "സേവന പ്രവൃത്തികൾ" to R.string.love_language_option_acts_of_service, // Malayalam
    "সেৱাৰ কাম" to R.string.love_language_option_acts_of_service, // Assamese
    "ਸੇਵਾ ਦੇ ਕੰਮ" to R.string.love_language_option_acts_of_service, // Punjabi

    // Receiving Gifts
    "Receiving Gifts" to R.string.love_language_option_receiving_gifts,
    "উপহার পাওয়া" to R.string.love_language_option_receiving_gifts, // Bengali
    "उपहार प्राप्त करना" to R.string.love_language_option_receiving_gifts, // Hindi
    "பரிசுகள் பெறுதல்" to R.string.love_language_option_receiving_gifts, // Tamil
    "ଉପହାର ଗ୍ରହଣ" to R.string.love_language_option_receiving_gifts, // Odia
    "బహుమతులు స్వీకరించడం" to R.string.love_language_option_receiving_gifts, // Telugu
    "भेटवस्तू स्वीकारणे" to R.string.love_language_option_receiving_gifts, // Marathi
    "ભેટો મેળવવી" to R.string.love_language_option_receiving_gifts, // Gujarati
    "ಉಡುಗೊರೆಗಳನ್ನು ಸ್ವೀಕರಿಸುವುದು" to R.string.love_language_option_receiving_gifts, // Kannada
    "സമ്മാനങ്ങൾ സ്വീകരിക്കൽ" to R.string.love_language_option_receiving_gifts, // Malayalam
    "উপহাৰ লাভ" to R.string.love_language_option_receiving_gifts, // Assamese
    "ਤੋਹਫ਼ੇ ਪ੍ਰਾਪਤ ਕਰਨਾ" to R.string.love_language_option_receiving_gifts, // Punjabi

    // Quality Time
    "Quality Time" to R.string.love_language_option_quality_time,
    "গুণগত সময়" to R.string.love_language_option_quality_time, // Bengali
    "गुणवत्तापूर्ण समय" to R.string.love_language_option_quality_time, // Hindi
    "தரமான நேரம்" to R.string.love_language_option_quality_time, // Tamil
    "ଗୁଣାତ୍ମକ ସମୟ" to R.string.love_language_option_quality_time, // Odia
    "నాణ్యమైన సమయం" to R.string.love_language_option_quality_time, // Telugu
    "गुणवत्तापूर्ण वेळ" to R.string.love_language_option_quality_time, // Marathi
    "ગુણવત્તાયુક્ત સમય" to R.string.love_language_option_quality_time, // Gujarati
    "ಗುಣಮಟ್ಟದ ಸಮಯ" to R.string.love_language_option_quality_time, // Kannada
    "ഗുണനിലവാരമുള്ള സമയം" to R.string.love_language_option_quality_time, // Malayalam
    "গুণগত সময়" to R.string.love_language_option_quality_time, // Assamese
    "ਗੁਣਵੱਤਾ ਵਾਲਾ ਸਮਾਂ" to R.string.love_language_option_quality_time, // Punjabi

    // Physical Touch
    "Physical Touch" to R.string.love_language_option_physical_touch,
    "শারীরিক স্পর্শ" to R.string.love_language_option_physical_touch, // Bengali
    "शारीरिक स्पर्श" to R.string.love_language_option_physical_touch, // Hindi
    "உடல் தொடுதல்" to R.string.love_language_option_physical_touch, // Tamil
    "ଶାରୀରିକ ସ୍ପର୍ଶ" to R.string.love_language_option_physical_touch, // Odia
    "శారీరక స్పర్శ" to R.string.love_language_option_physical_touch, // Telugu
    "शारीरिक स्पर्श" to R.string.love_language_option_physical_touch, // Marathi
    "શારીરિક સ્પર્શ" to R.string.love_language_option_physical_touch, // Gujarati
    "ದೈಹಿಕ ಸ್ಪರ್ಶ" to R.string.love_language_option_physical_touch, // Kannada
    "ശാരീരിക സ്പർശനം" to R.string.love_language_option_physical_touch, // Malayalam
    "শাৰীৰিক স্পৰ্শ" to R.string.love_language_option_physical_touch, // Assamese
    "ਸਰੀਰਕ ਸਪਰਸ਼" to R.string.love_language_option_physical_touch // Punjabi
)

val politicsNameToRes = mapOf(
    "Extrema izquierda" to R.string.politics_option_far_left,
    "Izquierda" to R.string.politics_option_left,
    "Centro-izquierda" to R.string.politics_option_centre_left,
    "Centro" to R.string.politics_option_centre,
    "Centro-derecha" to R.string.politics_option_centre_right,
    "Derecha" to R.string.politics_option_right,
    "Extrema derecha" to R.string.politics_option_far_right,
    "Liberal" to R.string.politics_option_liberal,
    "Conservador" to R.string.politics_option_conservative,
    "Moderado" to R.string.politics_option_moderate,
    "Socialista" to R.string.politics_option_socialist,
    "Comunista" to R.string.politics_option_communist,
    "Otro" to R.string.politics_option_other,
    // Far Left
    "Far left" to R.string.politics_option_far_left,
    "চরম বাম" to R.string.politics_option_far_left, // Bengali
    "अत्यंत वाम" to R.string.politics_option_far_left, // Hindi
    "தீவிர இடது" to R.string.politics_option_far_left, // Tamil
    "ଚରମ ବାମ" to R.string.politics_option_far_left, // Odia
    "అతి ఎడమ" to R.string.politics_option_far_left, // Telugu
    "अति डावे" to R.string.politics_option_far_left, // Marathi
    "અતિ ડાબે" to R.string.politics_option_far_left, // Gujarati
    "ತೀವ್ರ ಎಡ" to R.string.politics_option_far_left, // Kannada
    "അങ്ങേയറ്റം ഇടത്" to R.string.politics_option_far_left, // Malayalam
    "চৰম বাম" to R.string.politics_option_far_left, // Assamese
    "ਬਹੁਤ ਖੱਬੇ" to R.string.politics_option_far_left, // Punjabi

    // Left
    "Left" to R.string.politics_option_left,
    "বাম" to R.string.politics_option_left, // Bengali
    "वामपंथी" to R.string.politics_option_left, // Hindi
    "இடது" to R.string.politics_option_left, // Tamil
    "ବାମ" to R.string.politics_option_left, // Odia
    "ఎడమ" to R.string.politics_option_left, // Telugu
    "डावे" to R.string.politics_option_left, // Marathi
    "ડાબે" to R.string.politics_option_left, // Gujarati
    "ಎಡ" to R.string.politics_option_left, // Kannada
    "ഇടത്" to R.string.politics_option_left, // Malayalam
    "বাম" to R.string.politics_option_left, // Assamese
    "ਖੱਬੇ" to R.string.politics_option_left, // Punjabi

    // Centre-left
    "Centre-left" to R.string.politics_option_centre_left,
    "মধ্যবর্তী বাম" to R.string.politics_option_centre_left, // Bengali
    "मध्य-वाम" to R.string.politics_option_centre_left, // Hindi
    "மைய-இடது" to R.string.politics_option_centre_left, // Tamil
    "ମଧ୍ୟ-ବାମ" to R.string.politics_option_centre_left, // Odia
    "మధ్య-ఎడమ" to R.string.politics_option_centre_left, // Telugu
    "मध्य-डावे" to R.string.politics_option_centre_left, // Marathi
    "મધ્ય-ડાબે" to R.string.politics_option_centre_left, // Gujarati
    "ಮಧ್ಯ-ಎಡ" to R.string.politics_option_centre_left, // Kannada
    "മധ്യ-ഇടത്" to R.string.politics_option_centre_left, // Malayalam
    "মধ্য-বাম" to R.string.politics_option_centre_left, // Assamese
    "ਕੇਂਦਰ-ਖੱਬੇ" to R.string.politics_option_centre_left, // Punjabi

    // Centre
    "Centre" to R.string.politics_option_centre,
    "মধ্য" to R.string.politics_option_centre, // Bengali
    "मध्य" to R.string.politics_option_centre, // Hindi
    "மையம்" to R.string.politics_option_centre, // Tamil
    "ମଧ୍ୟ" to R.string.politics_option_centre, // Odia
    "మధ్య" to R.string.politics_option_centre, // Telugu
    "मध्य" to R.string.politics_option_centre, // Marathi
    "મધ્ય" to R.string.politics_option_centre, // Gujarati
    "ಮಧ್ಯ" to R.string.politics_option_centre, // Kannada
    "മധ്യം" to R.string.politics_option_centre, // Malayalam
    "মধ্য" to R.string.politics_option_centre, // Assamese
    "ਕੇਂਦਰ" to R.string.politics_option_centre, // Punjabi

    // Centre-right
    "Centre-right" to R.string.politics_option_centre_right,
    "মধ্যবর্তী ডান" to R.string.politics_option_centre_right, // Bengali
    "मध्य-दक्षिण" to R.string.politics_option_centre_right, // Hindi
    "மைய-வலது" to R.string.politics_option_centre_right, // Tamil
    "ମଧ୍ୟ-ଡାହାଣ" to R.string.politics_option_centre_right, // Odia
    "మధ్య-కుడి" to R.string.politics_option_centre_right, // Telugu
    "मध्य-उजवे" to R.string.politics_option_centre_right, // Marathi
    "મધ્ય-જમણે" to R.string.politics_option_centre_right, // Gujarati
    "ಮಧ್ಯ-ಬಲ" to R.string.politics_option_centre_right, // Kannada
    "മധ്യ-വലത്" to R.string.politics_option_centre_right, // Malayalam
    "মধ্য-সোঁ" to R.string.politics_option_centre_right, // Assamese
    "ਕੇਂਦਰ-ਸੱਜੇ" to R.string.politics_option_centre_right, // Punjabi

    // Right
    "Right" to R.string.politics_option_right,
    "ডান" to R.string.politics_option_right, // Bengali
    "दक्षिणपंथी" to R.string.politics_option_right, // Hindi
    "வலது" to R.string.politics_option_right, // Tamil
    "ଡାହାଣ" to R.string.politics_option_right, // Odia
    "కుడి" to R.string.politics_option_right, // Telugu
    "उजवे" to R.string.politics_option_right, // Marathi
    "જમણે" to R.string.politics_option_right, // Gujarati
    "ಬಲ" to R.string.politics_option_right, // Kannada
    "വലത്" to R.string.politics_option_right, // Malayalam
    "সোঁ" to R.string.politics_option_right, // Assamese
    "ਸੱਜੇ" to R.string.politics_option_right, // Punjabi

    // Far Right
    "Far right" to R.string.politics_option_far_right,
    "চরম ডান" to R.string.politics_option_far_right, // Bengali
    "अत्यंत दक्षिणपंथी" to R.string.politics_option_far_right, // Hindi
    "தீவிர வலது" to R.string.politics_option_far_right, // Tamil
    "ଚରମ ଡାହାଣ" to R.string.politics_option_far_right, // Odia
    "అతి కుడి" to R.string.politics_option_far_right, // Telugu
    "अति उजवे" to R.string.politics_option_far_right, // Marathi
    "અતિ જમણે" to R.string.politics_option_far_right, // Gujarati
    "ತೀವ್ರ ಬಲ" to R.string.politics_option_far_right, // Kannada
    "അങ്ങേയറ്റം വലത്" to R.string.politics_option_far_right, // Malayalam
    "চৰম সোঁ" to R.string.politics_option_far_right, // Assamese
    "ਬਹੁਤ ਸੱਜੇ" to R.string.politics_option_far_right, // Punjabi

    // Liberal
    "Liberal" to R.string.politics_option_liberal,
    "উদারপন্থী" to R.string.politics_option_liberal, // Bengali
    "उदार" to R.string.politics_option_liberal, // Hindi
    "தாராளவாதி" to R.string.politics_option_liberal, // Tamil
    "ଉଦାରବାଦୀ" to R.string.politics_option_liberal, // Odia
    "లిబరల్" to R.string.politics_option_liberal, // Telugu
    "उदारमतवादी" to R.string.politics_option_liberal, // Marathi
    "ઉદાર" to R.string.politics_option_liberal, // Gujarati
    "ಉದಾರವಾದಿ" to R.string.politics_option_liberal, // Kannada
    "ലിബറൽ" to R.string.politics_option_liberal, // Malayalam
    "উদাৰপন্থী" to R.string.politics_option_liberal, // Assamese
    "ਉਦਾਰ" to R.string.politics_option_liberal, // Punjabi

    // Conservative
    "Conservative" to R.string.politics_option_conservative,
    "রক্ষণশীল" to R.string.politics_option_conservative, // Bengali
    "रूढ़िवादी" to R.string.politics_option_conservative, // Hindi
    "பழமைவாதி" to R.string.politics_option_conservative, // Tamil
    "ରକ୍ଷଣଶୀଳ" to R.string.politics_option_conservative, // Odia
    "సంప్రదాయవాది" to R.string.politics_option_conservative, // Telugu
    "पुराणमतवादी" to R.string.politics_option_conservative, // Marathi
    "રૂઢિચુસ્ત" to R.string.politics_option_conservative, // Gujarati
    "ಸಂಪ್ರದಾಯವಾದಿ" to R.string.politics_option_conservative, // Kannada
    "യാഥാസ്ഥിതിക" to R.string.politics_option_conservative, // Malayalam
    "ৰক্ষণশীল" to R.string.politics_option_conservative, // Assamese
    "ਰੂੜ੍ਹੀਵਾਦੀ" to R.string.politics_option_conservative, // Punjabi

    // Moderate
    "Moderate" to R.string.politics_option_moderate,
    "মধ্যপন্থী" to R.string.politics_option_moderate, // Bengali
    "मध्यम" to R.string.politics_option_moderate, // Hindi
    "மிதவாதி" to R.string.politics_option_moderate, // Tamil
    "ମଧ୍ୟମପନ୍ଥୀ" to R.string.politics_option_moderate, // Odia
    "మోడరేట్" to R.string.politics_option_moderate, // Telugu
    "मध्यम" to R.string.politics_option_moderate, // Marathi
    "મધ્યમ" to R.string.politics_option_moderate, // Gujarati
    "ಮಿತವಾದಿ" to R.string.politics_option_moderate, // Kannada
    "മിതവാദി" to R.string.politics_option_moderate, // Malayalam
    "মধ্যপন্থী" to R.string.politics_option_moderate, // Assamese
    "ਮੱਧਮ" to R.string.politics_option_moderate, // Punjabi

    // Socialist
    "Socialist" to R.string.politics_option_socialist,
    "সমাজতান্ত্রিক" to R.string.politics_option_socialist, // Bengali
    "समाजवादी" to R.string.politics_option_socialist, // Hindi
    "சோசலிஸ்ட்" to R.string.politics_option_socialist, // Tamil
    "ସମାଜବାଦୀ" to R.string.politics_option_socialist, // Odia
    "సోషలిస్ట్" to R.string.politics_option_socialist, // Telugu
    "समाजवादी" to R.string.politics_option_socialist, // Marathi
    "સમાજવાદી" to R.string.politics_option_socialist, // Gujarati
    "ಸಮಾಜವಾದಿ" to R.string.politics_option_socialist, // Kannada
    "സോഷ്യലിസ്റ്റ്" to R.string.politics_option_socialist, // Malayalam
    "সমাজতান্ত্ৰিক" to R.string.politics_option_socialist, // Assamese
    "ਸਮਾਜਵਾਦੀ" to R.string.politics_option_socialist, // Punjabi

    // Communist
    "Communist" to R.string.politics_option_communist,
    "সাম্যবাদী" to R.string.politics_option_communist, // Bengali
    "साम्यवादी" to R.string.politics_option_communist, // Hindi
    "கம்யூனிஸ்ட்" to R.string.politics_option_communist, // Tamil
    "ସାମ୍ୟବାଦୀ" to R.string.politics_option_communist, // Odia
    "కమ్యూనిస్ట్" to R.string.politics_option_communist, // Telugu
    "साम्यवादी" to R.string.politics_option_communist, // Marathi
    "કોમ્યુનિસ્ટ" to R.string.politics_option_communist, // Gujarati
    "ಕಮ್ಯೂನಿಸ್ಟ್" to R.string.politics_option_communist, // Kannada
    "കമ്മ്യൂണിസ്റ്റ്" to R.string.politics_option_communist, // Malayalam
    "সাম্যবাদী" to R.string.politics_option_communist, // Assamese
    "ਕਮਿਉਨਿਸਟ" to R.string.politics_option_communist, // Punjabi

    // Other
    "Other" to R.string.politics_option_other,
    "অন্যান্য" to R.string.politics_option_other, // Bengali
    "अन्य" to R.string.politics_option_other, // Hindi
    "மற்றவை" to R.string.politics_option_other, // Tamil
    "ଅନ୍ୟାନ୍ୟ" to R.string.politics_option_other, // Odia
    "ఇతర" to R.string.politics_option_other, // Telugu
    "इतर" to R.string.politics_option_other, // Marathi
    "અન્ય" to R.string.politics_option_other, // Gujarati
    "ಇತರೆ" to R.string.politics_option_other, // Kannada
    "മറ്റുള്ളവ" to R.string.politics_option_other, // Malayalam
    "অন্যান্য" to R.string.politics_option_other, // Assamese
    "ਹੋਰ" to R.string.politics_option_other // Punjabi
)

