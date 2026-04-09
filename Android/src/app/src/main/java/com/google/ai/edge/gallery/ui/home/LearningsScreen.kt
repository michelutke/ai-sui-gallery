package com.google.ai.edge.gallery.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LearningArticle(
  val id: Int,
  val icon: ImageVector,
  val tags: List<Tag>,
  val title: String,
  val source: String,
  val description: String,
  val date: String,
  val readTime: String,
  val body: String,
)

data class Tag(
  val label: String,
  val color: Color,
)

val articles = listOf(
  LearningArticle(
    id = 0,
    icon = Icons.Rounded.Memory,
    tags = listOf(Tag("Recommended", Color(0xFF2E7D32)), Tag("Official", Color(0xFF1565C0))),
    title = "Gemma 4 in the AICore Developer Preview",
    source = "Google Android Developers Blog",
    description = "Google announces Gemma 4 E2B and E4B models available on-device via AICore, with up to 4x faster inference and 60% less battery use.",
    date = "Apr 2026",
    readTime = "5 min read",
    body = "Google has officially announced the availability of Gemma 4, their latest state-of-the-art open model, through the AICore Developer Preview on Android. The release brings two model variants to mobile devices: E4B (optimized for higher reasoning tasks) and E2B (3x faster, optimized for speed).\n\nThe performance improvements are significant: up to 4x faster inference compared to previous versions, with up to 60% less battery consumption. The models support enhanced reasoning, improved mathematical problem-solving, better temporal understanding for calendar and reminder applications, and superior image comprehension including OCR functionality.\n\nMultimodal capabilities span text, images, and audio processing, with support for over 140 languages. Developers can access the preview through Android Studio using ML Kit's Prompt API.\n\nThese preview models will eventually form the foundation for Gemini Nano 4, which Google plans to release later in 2026 on AICore-enabled devices. This announcement signals Google's strategy of making frontier-class AI capabilities available directly on Android hardware without cloud dependency.\n\nFor developers building on-device AI features, this means access to significantly more capable models that can handle complex reasoning, multimodal inputs, and agentic tasks — all running locally on the user's phone.",
  ),
  LearningArticle(
    id = 1,
    icon = Icons.Rounded.Code,
    tags = listOf(Tag("Technical", Color(0xFF6A1B9A)), Tag("Benchmarks", Color(0xFFE65100))),
    title = "Gemma 4: Frontier Multimodal Intelligence on Device",
    source = "Hugging Face Blog",
    description = "Comprehensive technical overview of Gemma 4's architecture, benchmarks, and deployment options across inference stacks.",
    date = "Apr 2026",
    readTime = "8 min read",
    body = "Hugging Face published a comprehensive technical overview of Gemma 4 on release day, covering the model family's four sizes: E2B (2.3B effective parameters), E4B (4.5B effective), 31B dense, and 26B A4B (a mixture-of-experts model with 4B activated / 26B total parameters).\n\nBenchmark data shows the 31B model achieving 85.2% on MMLU Pro, 89.2% on AIME 2026 (no tools), and 84.3% on GPQA Diamond. The smaller E4B and E2B models, designed specifically for on-device deployment, score 69.4% and 60.0% on MMLU Pro respectively — strong capability for their size class.\n\nArchitectural innovations include alternating local sliding-window and global full-context attention layers, Per-Layer Embeddings (PLE) for specialized conditioning, shared KV cache across later layers for memory efficiency, and configurable vision token budgets.\n\nMultimodal capabilities include image, text, audio, and video understanding out of the box, plus native function calling and tool use for agentic applications.\n\nDeployment is supported across every major inference stack: Transformers (Python), llama.cpp for local inference, MLX for Apple Silicon, Mistral.rs for Rust-native execution, and transformers.js for browser/WebGPU inference. The E2B and E4B models support GGUF and ONNX quantized formats for mobile and edge deployment. All models are released under the Apache 2.0 license.",
  ),
  LearningArticle(
    id = 2,
    icon = Icons.Rounded.Shield,
    tags = listOf(Tag("Strategy", Color(0xFF6A1B9A)), Tag("Privacy", Color(0xFF2E7D32))),
    title = "On-Device LLMs: Intelligence in the User's Pocket",
    source = "Dogtown Media",
    description = "The business case for on-device AI: privacy compliance, zero latency, offline availability, and cost elimination.",
    date = "Mar 2026",
    readTime = "10 min read",
    body = "This guide makes the business case for on-device LLMs across five strategic pillars.\n\nFirst, privacy and compliance: when AI runs on-device, user data never leaves the phone, eliminating data-in-transit risks and simplifying HIPAA, GDPR, and CCPA compliance. Research shows 63% of internet users believe companies are not transparent about data use, and 48% have stopped doing business with a company over privacy concerns.\n\nSecond, latency: on-device inference achieves up to 35% faster response times than cloud alternatives, with modern mobile NPUs processing tasks in under 5 milliseconds.\n\nThird, offline availability for the approximately 2.6 billion people worldwide without reliable internet.\n\nFourth, cost: enterprise LLM API spending surged from \$500 million in 2023 to \$8.4 billion by mid-2025, and on-device inference reduces marginal cost per call to effectively zero.\n\nFifth, personalization without surveillance, where models learn user preferences locally without transmitting behavioral data to servers.\n\nThe hardware revolution enables this shift: modern mobile NPUs now deliver over 70 TOPS with 8-24 GB unified memory, approaching the performance of an NVIDIA V100 data center GPU from 2017. Apple's Neural Engine, Google's Tensor chips, and Qualcomm's Snapdragon 8 Gen 3 all support models with 4+ billion parameters at conversational speeds on-device.",
  ),
  LearningArticle(
    id = 3,
    icon = Icons.Rounded.Speed,
    tags = listOf(Tag("Framework", Color(0xFF1565C0)), Tag("Official", Color(0xFF1565C0))),
    title = "LiteRT: The Universal Framework for On-Device AI",
    source = "Google Developers Blog",
    description = "LiteRT reaches production status with 1.4x faster GPU performance, NPU acceleration up to 100x faster than CPU.",
    date = "Jan 2026",
    readTime = "6 min read",
    body = "Google announced that LiteRT, the successor to TensorFlow Lite (renamed in September 2024), has reached production status as a comprehensive on-device AI framework.\n\nThe platform delivers 1.4x faster GPU performance than TFLite and introduces new NPU acceleration that reaches speeds up to 100x faster than CPU and 10x faster than GPU. Advanced features like asynchronous execution can deliver up to 2x faster performance in specific applications.\n\nLiteRT now provides cross-platform GPU support across Android, iOS, macOS, Windows, Linux, and Web through ML Drift, Google's next-generation GPU engine supporting OpenCL, OpenGL, Metal, and WebGPU.\n\nFor generative AI, the framework facilitates deployment of open models like Gemma through integrated tooling including the Torch Generative API, the LiteRT-LM orchestration layer (a dedicated runtime for LLM inference), and optimized conversion paths from PyTorch, TensorFlow, and JAX.\n\nThe framework maintains backward compatibility with the existing .tflite model format while introducing a modern CompiledModel API for advanced hardware acceleration. This positions LiteRT as the primary production-grade path for developers deploying on-device AI models on Android.",
  ),
  LearningArticle(
    id = 4,
    icon = Icons.Rounded.Smartphone,
    tags = listOf(Tag("Deep Dive", Color(0xFF6A1B9A)), Tag("Advanced", Color(0xFFE65100))),
    title = "On-Device LLMs in 2026: What Changed, What's Next",
    source = "Edge AI and Vision Alliance",
    description = "Memory bandwidth, not compute, is the real bottleneck. How sub-billion parameter models now handle practical tasks.",
    date = "Jan 2026",
    readTime = "12 min read",
    body = "This article argues that the biggest breakthroughs in on-device AI came not from faster chips but from rethinking how models are built, trained, compressed, and deployed.\n\nThe core thesis: memory bandwidth — not compute (TOPS) — is the real bottleneck. Mobile devices have 50-90 GB/s bandwidth versus data center GPUs at 2-3 TB/s, a 30-50x gap that dominates real throughput. This explains why 4-bit quantization has an outsized impact: it is not just 4x less storage but 4x less memory traffic per token.\n\nSub-billion parameter models now handle many practical tasks. There is convergence across major labs: Llama 3.2 (1B/3B), Gemma 3 (down to 270M), Phi-4 mini (3.8B), SmolLM2 (135M-1.7B), and Qwen2.5 (0.5B-1.5B). Training methodology and data quality drive capability at small scales more than raw parameter count — distilled small models can outperform base models many times larger on math and reasoning benchmarks.\n\nThe practical toolkit now includes mature quantization (GPTQ, AWQ, SmoothQuant, SpinQuant), KV cache management for long context, speculative decoding for 2-3x speedups, and structured pruning.\n\nThree frontiers stand out: Mixture-of-Experts on edge, test-time compute (where Llama 3.2 1B with search strategies can outperform the 8B model), and on-device personalization via local fine-tuning.\n\nThe bottom line: phones didn't become GPUs. The field learned to treat memory bandwidth, not compute, as the binding constraint.",
  ),
)

fun getArticleById(id: Int): LearningArticle? = articles.find { it.id == id }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearningsScreen(
  onArticleClick: (Int) -> Unit,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp),
  ) {
    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "Local AI Learnings",
      style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Our findings and recommendations from running AI models locally",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(24.dp))

    articles.forEach { article ->
      LearningCard(
        article = article,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onClick = { onArticleClick(article.id) },
      )
      Spacer(modifier = Modifier.height(16.dp))
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearningCard(
  article: LearningArticle,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onClick: () -> Unit,
) {
  with(sharedTransitionScope) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .sharedBounds(
          rememberSharedContentState(key = "article-card-${article.id}"),
          animatedVisibilityScope = animatedVisibilityScope,
          enter = fadeIn(animationSpec = tween(300)),
          exit = fadeOut(animationSpec = tween(300)),
        )
        .clickable(onClick = onClick),
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = article.icon,
              contentDescription = null,
              modifier = Modifier
                .padding(8.dp)
                .sharedBounds(
                  rememberSharedContentState(key = "article-icon-${article.id}"),
                  animatedVisibilityScope = animatedVisibilityScope,
                ),
              tint = MaterialTheme.colorScheme.primary,
            )
          }

          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            article.tags.forEach { tag ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = tag.color.copy(alpha = 0.15f),
              ) {
                Text(
                  text = tag.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = tag.color,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = article.title,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.sharedBounds(
            rememberSharedContentState(key = "article-title-${article.id}"),
            animatedVisibilityScope = animatedVisibilityScope,
          ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = article.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${article.date}  \u2022  ${article.readTime}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              modifier = Modifier.padding(6.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleDetailScreen(
  article: LearningArticle,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  with(sharedTransitionScope) {
    Column(
      modifier = modifier
        .fillMaxSize()
        .sharedBounds(
          rememberSharedContentState(key = "article-card-${article.id}"),
          animatedVisibilityScope = animatedVisibilityScope,
          enter = fadeIn(animationSpec = tween(300)),
          exit = fadeOut(animationSpec = tween(300)),
        )
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .verticalScroll(rememberScrollState()),
    ) {
      Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = article.icon,
              contentDescription = null,
              modifier = Modifier
                .padding(8.dp)
                .sharedBounds(
                  rememberSharedContentState(key = "article-icon-${article.id}"),
                  animatedVisibilityScope = animatedVisibilityScope,
                ),
              tint = MaterialTheme.colorScheme.primary,
            )
          }

          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            article.tags.forEach { tag ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = tag.color.copy(alpha = 0.15f),
              ) {
                Text(
                  text = tag.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = tag.color,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = article.title,
          style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.sharedBounds(
            rememberSharedContentState(key = "article-title-${article.id}"),
            animatedVisibilityScope = animatedVisibilityScope,
          ),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "${article.source}  \u2022  ${article.date}  \u2022  ${article.readTime}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        article.body.split("\n\n").forEach { paragraph ->
          Text(
            text = paragraph,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }
}
