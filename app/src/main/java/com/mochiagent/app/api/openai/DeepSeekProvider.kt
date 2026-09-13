package com.mochiagent.app.api.openai

import com.mochiagent.app.util.Constants

class DeepSeekProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_DEEPSEEK
    override val defaultBaseUrl: String = "https://api.deepseek.com"
    // Reasoning/content parsing uses BaseOpenAiProvider's default (reasoning_content + content).
}
