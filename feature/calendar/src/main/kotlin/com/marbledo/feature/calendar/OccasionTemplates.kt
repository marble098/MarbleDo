package com.marbledo.feature.calendar

import com.marbledo.domain.model.ChecklistItem
import com.marbledo.domain.model.Task
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class PreparationTemplate { SIMPLE, FAMILY, CULTURAL, SCHOOL }

/** Small editable checklists, not automatically scheduled or mandated activities. */
object OccasionTemplates {
    fun checklist(template: PreparationTemplate, languageTag: String): List<String> {
        val fa = languageTag == "fa"
        return when (template) {
            PreparationTemplate.SIMPLE -> if (fa) listOf("بررسی تاریخ و جزئیات مناسبت", "آماده‌کردن وسایل موردنیاز", "هماهنگی با افراد مرتبط")
                else listOf("Check the occasion date and details", "Prepare materials", "Coordinate with participants")
            PreparationTemplate.FAMILY -> if (fa) listOf("هماهنگی زمان با خانواده", "تعیین بودجه و فهرست خرید", "آماده‌کردن هدیه یا پیام", "ثبت خاطره و قدردانی")
                else listOf("Agree on a time with family", "Set a budget and shopping list", "Prepare a gift or message", "Record a memory and say thanks")
            PreparationTemplate.CULTURAL -> if (fa) listOf("انتخاب متن یا اثر فرهنگی معتبر", "برنامه‌ریزی مطالعه یا بازدید", "تهیهٔ ابزار و منابع", "اشتراک آموخته‌ها")
                else listOf("Choose a reliable cultural text or work", "Plan a reading session or visit", "Gather materials and references", "Share what you learned")
            PreparationTemplate.SCHOOL -> if (fa) listOf("تطبیق تاریخ با بخشنامهٔ منطقه", "تعیین هدف تربیتی و گروه سنی", "تقسیم مسئولیت با دانش‌آموزان", "هماهنگی ایمنی، دسترس‌پذیری و رضایت اولیا", "آماده‌سازی محتوا و امکانات", "ارزیابی و ثبت گزارش کوتاه")
                else listOf("Confirm the date with the district circular", "Set the educational aim and age group", "Assign roles with students", "Arrange safety, accessibility and parental consent", "Prepare content and equipment", "Evaluate and write a brief report")
        }
    }

    fun draft(request: OccasionPlanRequest, zone: ZoneId = ZoneId.systemDefault()): Task = Task(
        title = request.title,
        description = request.description,
        tags = listOfNotNull(request.planningTag),
        dueAtEpochMillis = request.dateIso?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() },
        isAllDay = request.dateIso != null,
        countdownEnabled = request.asCountdown,
        checklist = request.checklist.map { ChecklistItem(UUID.randomUUID().toString(), it) },
    )
}
