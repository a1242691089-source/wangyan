package com.huangder.lumibooks.domain.model

/**
 * Six note intent types from the product plan (V1.0, section 五.2).
 *
 * The enum is a **read-side intent view** only: [Note] keeps storing the legacy
 * string in `NoteEntity.type` (DB version 20, WebDAV / backup / migration all
 * pass the raw string through). Never write [NoteType.value] for legacy
 * families — see [Note.noteType] for how stored strings derive the intent.
 */
enum class NoteType(val value: String) {
    /** 原文摘录：作者原文（stored as "highlight" with no attached comment text）. */
    EXCERPT("highlight"),

    /** 阅读批注：对原文的解释/补充（legacy "note"、underline/highlight + comment text）. */
    COMMENT("note"),

    /** 个人思考：用户自己的理解、观点、推论。 */
    THOUGHT("thought"),

    /** 疑问：尚未解决、可继续研究的问题。 */
    QUESTION("question"),

    /** 灵感：值得后续整理的想法。 */
    INSPIRATION("inspiration"),

    /** AI 摘要：模型生成内容，展示时必须带 AI 来源标识。创建入口随 AI 引擎（V0.5）开放。 */
    AI_SUMMARY("ai_summary");

    companion object {
        fun fromValue(raw: String): NoteType? = entries.firstOrNull { it.value == raw }
    }
}
