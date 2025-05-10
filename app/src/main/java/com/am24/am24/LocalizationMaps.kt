package com.am24.am24

import android.content.Context
import java.util.Calendar

// ─── Basic Info ────────────────────────────────────
object LocalizationMaps {
    //── 1) How to format your “joined on” date string ──
    //    e.g. English:  Feb 04, 2025
    //         Bengali:  ০৪ ফেব্রুয়ারি ২০২৫
    //         Hindi:    ०४ फ़रवरी २०२५
    val joinedOnDateFormat = mapOf(
        "en" to "%s %02d, %d",   // monthName day, year
        "bn" to "%s %02d, %d",    // day monthName year
        "hi" to "%s %02d, %d"
    )

    // month names, full form
    val monthNames = mapOf(
        "en" to listOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        ),
        "bn" to listOf(
            "জানুয়ারি","ফেব্রুয়ারি","মার্চ","এপ্রিল","মে","জুন",
            "জুলাই","আগস্ট","সেপ্টেম্বর","অক্টোবর","নভেম্বর","ডিসেম্বর"
        ),
        "hi" to listOf(
            "जनवरी","फ़रवरी","मार्च","अप्रैल","मई","जून",
            "जुलाई","अगस्त","सितंबर","अक्टूबर","नवंबर","दिसंबर"
        )
    )
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

// Caste (English, Bengali, Hindi)
val casteNameToRes = mapOf(
    "Kulin Brahmin" to R.string.caste_kulin_brahmin,
    "কুলীন ব্রাহ্মণ" to R.string.caste_kulin_brahmin,
    "कुलीन ब्राह्मण" to R.string.caste_kulin_brahmin,

    "Non-Kulin Brahmin" to R.string.caste_non_kulin_brahmin,
    "অকুলীন ব্রাহ্মণ" to R.string.caste_non_kulin_brahmin,
    "नकुलीन ब्राह्मण" to R.string.caste_non_kulin_brahmin,

    "Kulin Kayastha" to R.string.caste_kulin_kayastha,
    "কুলীন কায়স্থ" to R.string.caste_kulin_kayastha,
    "कुलीन कायस्थ" to R.string.caste_kulin_kayastha,

    "Non-Kulin Kayastha" to R.string.caste_non_kulin_kayastha,
    "অকুলীন কায়স্থ" to R.string.caste_non_kulin_kayastha,
    "नकुलीन कायस्थ" to R.string.caste_non_kulin_kayastha,

    "Baidya" to R.string.caste_baidya,
    "বৈদ্য" to R.string.caste_baidya,
    "बैद्य" to R.string.caste_baidya,

    "Kshatriya" to R.string.caste_kshatriya,
    "ক্ষত্রিয়" to R.string.caste_kshatriya,
    "क्षत्रिय" to R.string.caste_kshatriya,

    "Vaishya" to R.string.caste_vaishya,
    "বৈশ্য" to R.string.caste_vaishya,
    "वैश्य" to R.string.caste_vaishya,

    "Rajbonshi" to R.string.caste_rajbonshi,
    "রাজবংশী" to R.string.caste_rajbonshi,
    "राजबंशी" to R.string.caste_rajbonshi,

    "Sadgop" to R.string.caste_sadgop,
    "সদগোপ" to R.string.caste_sadgop,
    "सदगोप" to R.string.caste_sadgop,

    "Mahishya" to R.string.caste_mahishya,
    "মহিষ্য" to R.string.caste_mahishya,
    "महिष्य" to R.string.caste_mahishya,

    "Scheduled Caste" to R.string.caste_scheduled_caste,
    "অনুসূচি জাতি" to R.string.caste_scheduled_caste,
    "अनुसूचित जाति" to R.string.caste_scheduled_caste,

    "Scheduled Tribe" to R.string.caste_scheduled_tribe,
    "অনুসূচি অধিনস্ত জনগোষ্ঠী" to R.string.caste_scheduled_tribe,
    "अनुसूचित जनजाति" to R.string.caste_scheduled_tribe,

    "OBC" to R.string.caste_obc,
    "অন্যান্য পিছিয়ে পড়া শ্রেণি" to R.string.caste_obc,
    "अविकसित पिछड़ा वर्ग" to R.string.caste_obc,

    "General" to R.string.caste_general,
    "সাধারণ" to R.string.caste_general,
    "सामान्य" to R.string.caste_general,

    "Other" to R.string.caste_other,
    "অন্যান্য" to R.string.caste_other,
    "अन्य" to R.string.caste_other
)

// Gender (English, Bengali, Hindi)
val genderNameToRes = mapOf(
    "Male" to R.string.male_option,
    "পুরুষ" to R.string.male_option,
    "पुरुष" to R.string.male_option,

    "Female" to R.string.female_option,
    "মহিলা" to R.string.female_option,
    "महिला" to R.string.female_option,

    "Other" to R.string.college_other,
    "অন্যান্য" to R.string.college_other,
    "अन्य" to R.string.college_other
)

// Community (English, Bengali, Hindi)
val communityNameToRes = mapOf(
    // Generic communities
    "Bengali"               to R.string.community_bengali,
    "বাঙালি"                 to R.string.community_bengali,
    "बंगाली"                 to R.string.community_bengali,

    "Santhal"               to R.string.community_santhal,
    "সাঁওতাল"                to R.string.community_santhal,
    "संताल"                 to R.string.community_santhal,

    "Oraon"                 to R.string.community_oraon,
    "ওড়াঁও"                 to R.string.community_oraon,
    "उरांव"                 to R.string.community_oraon,

    "Munda"                 to R.string.community_munda,
    "মুন্ডা"                 to R.string.community_munda,
    "मुंडा"                 to R.string.community_munda,

    "Punjabi"               to R.string.community_punjabi,
    "পাঞ্জাবি"               to R.string.community_punjabi,
    "पंजाबी"                to R.string.community_punjabi,

    "Tamil"                 to R.string.community_tamil,
    "তামিল"                 to R.string.community_tamil,
    "तमिल"                  to R.string.community_tamil,

    "Gujarati"              to R.string.community_gujarati,
    "গুজরাটি"               to R.string.community_gujarati,
    "गुजराती"               to R.string.community_gujarati,

    "Marwari"               to R.string.community_marwari,
    "মারোয়াড়ি"            to R.string.community_marwari,
    "मारवाड़ी"              to R.string.community_marwari,

    "Bihari"                to R.string.community_bihari,
    "বিহারী"                to R.string.community_bihari,
    "बिहारी"                to R.string.community_bihari,

    "Odia"                  to R.string.community_odia,
    "ওড়িয়া"                to R.string.community_odia,
    "उड़िया"                 to R.string.community_odia,

    "Assamese"              to R.string.community_assamese,
    "অসমীয়া"               to R.string.community_assamese,
    "असमिया"                to R.string.community_assamese,

    "Telugu"                to R.string.community_telugu,
    "তেলুগু"                to R.string.community_telugu,
    "तेलुगु"                to R.string.community_telugu,

    "Kannadiga"             to R.string.community_kannadiga,
    "কন্নড়"                 to R.string.community_kannadiga,
    "कन्नड़"                to R.string.community_kannadiga,

    "Malayali"              to R.string.community_malayali,
    "মালয়ালি"               to R.string.community_malayali,
    "मलयाली"               to R.string.community_malayali,

    "Nepali"                to R.string.community_nepali,
    "নেপালি"                to R.string.community_nepali,
    "नेपाली"               to R.string.community_nepali,

    "Bangal"                to R.string.community_bangal,
    "বাঙাল"                 to R.string.community_bangal,
    "बांगाल"                to R.string.community_bangal,

    "Ghoti"                 to R.string.community_ghoti,
    "ঘটি"                   to R.string.community_ghoti,
    "घोटी"                 to R.string.community_ghoti,

    "Other"                 to R.string.community_other,
    "অন্যান্য"               to R.string.community_other,
    "अन्य"                  to R.string.community_other,

    // Himalayan neighbours
    "Bhutanese"             to R.string.community_bhutanese,
    "ভুটানি"                to R.string.community_bhutanese,
    "भूटानी"               to R.string.community_bhutanese,

    "Sikkimese"             to R.string.community_sikkimese,
    "সিকিমি"                to R.string.community_sikkimese,
    "सिक्कीमी"              to R.string.community_sikkimese,

    "Arunachali"            to R.string.community_arunachali,
    "অরুণাচলি"             to R.string.community_arunachali,
    "अरुणाचली"             to R.string.community_arunachali,

    // North-East India: Sonowal Kachari
    "Sonowal Kachari"       to R.string.community_sonowal_kachari,
    "সোনোয়াল কছারি"        to R.string.community_sonowal_kachari,
    "सोनवाल कछारी"         to R.string.community_sonowal_kachari
)

// Religion (English, Bengali, Hindi)
val religionNameToRes = mapOf(
    // Hindu
    "Hindu"                  to R.string.religion_hindu,
    "হিন্দু"                  to R.string.religion_hindu,
    "हिंदू"                   to R.string.religion_hindu,

    // Muslim
    "Muslim"                 to R.string.religion_muslim,
    "মুসলিম"                to R.string.religion_muslim,
    "मुस्लिम"               to R.string.religion_muslim,

    // Christian
    "Christian"              to R.string.religion_christian,
    "খ্রিস্টান"               to R.string.religion_christian,
    "ईसाई"                  to R.string.religion_christian,

    // Sikh
    "Sikh"                   to R.string.religion_sikh,
    "সিখ"                    to R.string.religion_sikh,
    "सिख"                    to R.string.religion_sikh,

    // Buddhist
    "Buddhist"               to R.string.religion_buddhist,
    "বৌদ্ধ"                  to R.string.religion_buddhist,
    "बौद्ध"                  to R.string.religion_buddhist,

    // Jain
    "Jain"                   to R.string.religion_jain,
    "জৈন"                   to R.string.religion_jain,
    "जैन"                   to R.string.religion_jain,

    // No religion
    "No religion"            to R.string.religion_no_religion,
    "কোনও ধর্ম নেই"          to R.string.religion_no_religion,
    "कोई धर्म नहीं"           to R.string.religion_no_religion,

    // Indigenous / Tribal
    "Indigenous/Tribal"      to R.string.religion_indigenous_tribal,
    "আদিবাসী/উপজাতি"         to R.string.religion_indigenous_tribal,
    "स्वदेशी/जनजातीय"         to R.string.religion_indigenous_tribal,

    // Other
    "Other"                  to R.string.religion_other,
    "অন্যান্য"                to R.string.religion_other,
    "अन्य"                   to R.string.religion_other
)

// City (English, Bengali, Hindi)
val cityNameToRes = mapOf(
    // Kolkata
    "Kolkata"           to R.string.city_kolkata,
    "কলকাতা"            to R.string.city_kolkata,
    "कोलकाता"           to R.string.city_kolkata,

    // Howrah
    "Howrah"            to R.string.city_howrah,
    "হাওড়া"             to R.string.city_howrah,
    "हावड़ा"            to R.string.city_howrah,

    // Durgapur
    "Durgapur"          to R.string.city_durgapur,
    "দুর্গাপুর"          to R.string.city_durgapur,
    "दुर्गापुर"         to R.string.city_durgapur,

    // Asansol
    "Asansol"           to R.string.city_asansol,
    "আসানসোল"           to R.string.city_asansol,
    "आसनसोल"           to R.string.city_asansol,

    // Siliguri
    "Siliguri"          to R.string.city_siliguri,
    "শিলিগুড়ি"         to R.string.city_siliguri,
    "शिलिगुड़ी"        to R.string.city_siliguri,

    // Darjeeling
    "Darjeeling"        to R.string.city_darjeeling,
    "দার্জিলিং"         to R.string.city_darjeeling,
    "दार्जिलिंग"       to R.string.city_darjeeling,

    // Malda
    "Malda"             to R.string.city_malda,
    "মালদা"             to R.string.city_malda,
    "मालदा"            to R.string.city_malda,

    // Jalpaiguri
    "Jalpaiguri"        to R.string.city_jalpaiguri,
    "জলপাইগুড়ি"        to R.string.city_jalpaiguri,
    "जलपाईगुड़ी"       to R.string.city_jalpaiguri,

    // Cooch Behar
    "Cooch Behar"       to R.string.city_cooch_behar,
    "কোচবিহার"          to R.string.city_cooch_behar,
    "कूच बिहार"        to R.string.city_cooch_behar,

    // Alipurduar
    "Alipurduar"        to R.string.city_alipurduar,
    "আলিপুরদুয়ার"      to R.string.city_alipurduar,
    "अलीपुरद्वार"      to R.string.city_alipurduar,

    // Bankura
    "Bankura"           to R.string.city_bankura,
    "বাঁকুড়া"           to R.string.city_bankura,
    "बांकुरा"           to R.string.city_bankura,

    // Purulia
    "Purulia"           to R.string.city_purulia,
    "পুরুলিয়া"          to R.string.city_purulia,
    "पुरुलिया"          to R.string.city_purulia,

    // Kharagpur
    "Kharagpur"         to R.string.city_kharagpur,
    "খড়গপুর"           to R.string.city_kharagpur,
    "खड़गपुर"           to R.string.city_kharagpur,

    // Midnapore
    "Midnapore"         to R.string.city_midnapore,
    "মেদিনীপুর"         to R.string.city_midnapore,
    "मेदिनिपुर"         to R.string.city_midnapore,

    // Bardhaman
    "Bardhaman"         to R.string.city_bardhaman,
    "বর্ধমান"           to R.string.city_bardhaman,
    "बर्धमान"           to R.string.city_bardhaman,

    // Hooghly
    "Hooghly"           to R.string.city_hooghly,
    "হুগলি"            to R.string.city_hooghly,
    "हुगली"             to R.string.city_hooghly,

    // Murshidabad
    "Murshidabad"       to R.string.city_murshidabad,
    "মুর্শিদাবাদ"        to R.string.city_murshidabad,
    "मुर्शिदाबाद"       to R.string.city_murshidabad,

    // Baharampur
    "Baharampur"        to R.string.city_baharampur,
    "বেহারামপুর"        to R.string.city_baharampur,
    "बहरामपुर"          to R.string.city_baharampur,

    // Haldia
    "Haldia"            to R.string.city_haldia,
    "হালদিয়া"          to R.string.city_haldia,
    "हालदिया"          to R.string.city_haldia,

    // Ranaghat
    "Ranaghat"          to R.string.city_ranaghat,
    "রাণাঘাট"           to R.string.city_ranaghat,
    "राणाघाट"           to R.string.city_ranaghat,

    // Kalyani
    "Kalyani"           to R.string.city_kalyani,
    "কল্যাণী"           to R.string.city_kalyani,
    "कल्याणी"           to R.string.city_kalyani,

    // Chandannagar
    "Chandannagar"      to R.string.city_chandannagar,
    "চন্দননগর"          to R.string.city_chandannagar,
    "चंदननगर"          to R.string.city_chandannagar,

    // Other
    "Other"             to R.string.city_other,
    "অন্যান্য"           to R.string.city_other,
    "अन्य"              to R.string.city_other
)


// ─── Preferences ──────────────────────────────────

// Looking For (English, Bengali, Hindi)
val lookingForNameToRes = mapOf(
    "Casual Sex" to R.string.looking_for_casual_sex,
    "যৌন সম্পর্ক" to R.string.looking_for_casual_sex,
    "असामयিক संबंध" to R.string.looking_for_casual_sex,

    "Connection" to R.string.looking_for_connection,
    "সংযোগ" to R.string.looking_for_connection,
    "कनेक्शन" to R.string.looking_for_connection,

    "Partner" to R.string.looking_for_partner,
    "সঙ্গী" to R.string.looking_for_partner,
    "साथी" to R.string.looking_for_partner,

    "Marriage" to R.string.looking_for_marriage,
    "বিবাহ" to R.string.looking_for_marriage,
    "विवाह" to R.string.looking_for_marriage
)

// Love Language (English, Bengali, Hindi)
val loveLanguageNameToRes = mapOf(
    "Words of Affirmation" to R.string.love_language_option_words_of_affirmation,
    "প্রশংসার শব্দ" to R.string.love_language_option_words_of_affirmation,
    "प्रशंसा के शब्द" to R.string.love_language_option_words_of_affirmation,

    "Acts of Service" to R.string.love_language_option_acts_of_service,
    "পরিষেবার কাজ" to R.string.love_language_option_acts_of_service,
    "सेवा कार्य" to R.string.love_language_option_acts_of_service,

    "Receiving Gifts" to R.string.love_language_option_receiving_gifts,
    "Receiving gifts" to R.string.love_language_option_receiving_gifts,
    "উপহার পাওয়া" to R.string.love_language_option_receiving_gifts,
    "उपहार प्राप्त करना" to R.string.love_language_option_receiving_gifts,

    "Quality Time" to R.string.love_language_option_quality_time,
    "গুণগত সময়" to R.string.love_language_option_quality_time,
    "गुणवत्तापूर्ण समय" to R.string.love_language_option_quality_time,

    "Physical Touch" to R.string.love_language_option_physical_touch,
    "শারীরিক স্পর্শ" to R.string.love_language_option_physical_touch,
    "शारीरिक स्पर्श" to R.string.love_language_option_physical_touch
)

// Politics (English, Bengali, Hindi)
val politicsNameToRes = mapOf(
    "Liberal" to R.string.politics_option_liberal,
    "উদারপ্রিয়" to R.string.politics_option_liberal,
    "उदारवादी" to R.string.politics_option_liberal,

    "Moderate" to R.string.politics_option_moderate,
    "মধ্যম" to R.string.politics_option_moderate,
    "मध्यम" to R.string.politics_option_moderate,

    "Conservative" to R.string.politics_option_conservative,
    "সংরক্ষক" to R.string.politics_option_conservative,
    "संरक्षणवादी" to R.string.politics_option_conservative
)

