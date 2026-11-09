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
            PreparationTemplate.SIMPLE -> if (fa) listOf("تاریخ مناسبت را چک کن", "وسایل لازم را آماده کن", "با بقیه هماهنگ کن")
                else listOf("Check the date and details", "Get what you need ready", "Coordinate with the others")
            PreparationTemplate.FAMILY -> if (fa) listOf("با خانواده وقت هماهنگ کن", "بودجه و فهرست خرید را بنویس", "هدیه یا پیام آماده کن", "یادش را گرامی بدار")
                else listOf("Agree on a time with family", "Write a budget and shopping list", "Prepare a gift or message", "Mark the memory")
            PreparationTemplate.CULTURAL -> if (fa) listOf("یک متن یا اثر خوب انتخاب کن", "برای خواندن یا بازدید برنامه بگذار", "منابع را جمع کن", "چیزی که یاد گرفتی را بگو")
                else listOf("Pick a good text or work", "Plan a reading or a visit", "Collect the sources", "Share what you learned")
            PreparationTemplate.SCHOOL -> if (fa) listOf("تاریخ را با اطلاعیهٔ مدرسه چک کن", "هدف و گروه سنی را مشخص کن", "کارها را بین دانش‌آموزان تقسیم کن", "ایمنی و اجازهٔ والدین را هماهنگ کن", "محتوا و وسایل را آماده کن", "یک گزارش کوتاه بنویس")
                else listOf("Check the date with the school notice", "Pick the aim and age group", "Split the jobs with students", "Arrange safety and parent consent", "Prepare content and equipment", "Write a short report")
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
