# veida-java

Generate images from Java with **no API key, no account and no card**.

```java
import io.github.hanshs474.veida.Veida;

Veida.Image img = Veida.create().generate("a paper boat on a calm lake at sunrise, soft light", "16:9");
System.out.println(img.url());         // https://cdn.veida.ai/uploads/....webp
System.out.println(img.watermarked()); // true on the free tier
```

Install:

```xml
<dependency>
  <groupId>io.github.hanshs474</groupId>
  <artifactId>veida</artifactId>
  <version>0.1.0</version>
</dependency>
```

Most image libraries want a key from OpenAI, fal or Replicate before they run once.
This one talks to the anonymous tier of [Veida](https://veida.ai/?utm_source=maven&utm_medium=package), an online AI image generator,
using a client id the library invents rather than an account you register, so it works on a
fresh machine with nothing configured.

## What the free tier covers

Measured against the running service:

| | |
|---|---|
| Anonymous grant per client id | 4 credits |
| One text-to-image run | 4 credits, so one image per id |
| Per-IP ceiling | 30 credits a day, roughly 7 images |
| Output | 1K, watermarked |

The library mints a fresh client id for every call, so a loop is not limited to one image;
the per-IP ceiling is what stops it. When that is spent you get a `VeidaException` with reason `QUOTA` saying so.

Editing an existing photo is **not** offered here. The editing model costs 30 credits against
a 4-credit grant, so an anonymous edit could never be paid for. Signed in, the
[image editor](https://veida.ai/image/chatgpt-image-editor?utm_source=maven&utm_medium=package) does that in a browser.

## Past the free tier

Signing in removes the watermark, lifts the cap and opens the larger models:
[GPT Image 2](https://veida.ai/image/gpt-image-2?utm_source=maven&utm_medium=package) when the picture must contain readable text,
[Nano Banana 2](https://veida.ai/image/nano-banana-2?utm_source=maven&utm_medium=package) for instruction following and
[Seedream 5.0 Lite](https://veida.ai/image/seedream-5-lite?utm_source=maven&utm_medium=package) for colour and composition.
What each costs is on the [pricing page](https://veida.ai/pricing?utm_source=maven&utm_medium=package).

## Prompts that work

Name the light, the material and the composition. "A product photo of a mug" gives the model
nothing; "matte black ceramic mug on pale oak, soft window light from the left, shallow depth
of field" gives it a picture. Worked examples live in the
[prompt library](https://veida.ai/image/prompts?utm_source=maven&utm_medium=package). The same engine backs the
[ChatGPT image generator](https://veida.ai/image/chatgpt-image-generator?utm_source=maven&utm_medium=package) and the
[free AI image generator](https://veida.ai/image?utm_source=maven&utm_medium=package) in a browser.

## API

```java
Veida.create().generate(String prompt, String aspectRatio) -> Veida.Image  throws VeidaException
Veida.builder().timeout(Duration).pollEvery(Duration).httpClient(HttpClient).build()

VeidaException.Reason: QUOTA (wait or sign in) · REJECTED (reword) · TIMEOUT (retry) · OTHER
```

Aspect ratio is one of `1:1`, `16:9`, `9:16`, `4:3`, `3:4`.

Zero dependencies, Java 11+, built on `java.net.http.HttpClient`. Instances are immutable and thread-safe. Gradle: `implementation("io.github.hanshs474:veida:0.1.0")`.

Other clients: [Python](https://pypi.org/project/veida/) ·
[Go](https://pkg.go.dev/github.com/hanshs474/veida-go) ·
[MCP server](https://www.npmjs.com/package/veida-mcp).

MIT.
