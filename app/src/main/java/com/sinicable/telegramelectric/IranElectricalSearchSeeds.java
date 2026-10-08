package com.sinicable.telegramelectric;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class IranElectricalSearchSeeds {
    private static final String[] TOPICS = {
            "برق صنعتی",
            "برق ساختمان",
            "برق قدرت",
            "مهندسی برق",
            "تاسیسات برقی",
            "تجهیزات برق",
            "تابلو برق",
            "تابلو برق صنعتی",
            "مونتاژ تابلو برق",
            "طراحی تابلو برق",
            "برقکار صنعتی",
            "برقکار ساختمان",
            "کابل برق",
            "سیم برق",
            "سیم و کابل",
            "کابل فشار ضعیف",
            "کابل فشار متوسط",
            "ترانسفورماتور",
            "ترانس برق",
            "پست برق",
            "کلید قدرت",
            "کلید مینیاتوری",
            "کلید اتوماتیک",
            "فیوز برق",
            "کنتاکتور",
            "رله برق",
            "رله حفاظتی",
            "حفاظت الکتریکی",
            "ارتینگ",
            "سیستم ارت",
            "صاعقه گیر",
            "روشنایی",
            "روشنایی صنعتی",
            "موتور برق",
            "الکتروموتور",
            "درایو موتور",
            "اینورتر",
            "کنترل دور موتور",
            "اتوماسیون صنعتی",
            "پی ال سی",
            "کنترل صنعتی",
            "ابزار دقیق",
            "سنسور صنعتی",
            "ژنراتور برق",
            "دیزل ژنراتور",
            "یو پی اس",
            "برق اضطراری",
            "انرژی خورشیدی",
            "پنل خورشیدی",
            "نیروگاه خورشیدی"
    };

    private static final String[] TEMPLATES = {
            "گروه %s",
            "گروه تلگرام %s",
            "%s تهران",
            "%s مشهد",
            "%s اصفهان",
            "%s شیراز",
            "%s تبریز",
            "%s ایران",
            "%s تلگرام",
            "%s بازار ایران",
            "%s متخصصان ایران",
            "%s مهندسان ایران",
            "%s تکنسین های ایران",
            "%s پیمانکاران ایران",
            "%s فروشندگان ایران",
            "%s خرید و فروش ایران",
            "%s آموزش ایران",
            "%s پروژه ایران",
            "%s صنعت ایران",
            "%s انجمن ایران",
    };

    static List<String> build() {
        Set<String> unique = new LinkedHashSet<>(1000);
        for (String topic : TOPICS) {
            for (String template : TEMPLATES) {
                unique.add(String.format(template, topic));
            }
        }

        if (unique.size() != 1000) {
            throw new IllegalStateException("Iran electrical seed pack must contain exactly 1000 unique queries.");
        }

        return new ArrayList<>(unique);
    }

    private IranElectricalSearchSeeds() {
    }
}
