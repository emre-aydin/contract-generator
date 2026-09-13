# contract-generator

Java library that renders a **Thymeleaf HTML contract** into a **PDF with fillable AcroForm
fields**, ready to hand off to an e-signature service such as DocuSign.

Fillable regions are declared as ordinary **HTML form controls** (`<input>`, `<textarea>`,
checkbox, radio) in the template. [openhtmltopdf](https://github.com/danfickle/openhtmltopdf)
renders them directly into interactive AcroForm fields — there are no anchor-text placeholders or
coordinate math. Apache PDFBox then validates and normalizes the form.

## Requirements

- **Java 25** (Temurin tested)
- Maven — use the bundled wrapper (`./mvnw`); no global installation needed.

## Build & test

```bash
./mvnw test          # compile + run the test suite
./mvnw -Dtest=ContractGeneratorTest#generatesPdfWithExpectedFillableFields test   # single test
./mvnw package       # build the jar
```

## Usage

```java
ContractData data = ContractData.builder()
        .put("title", "Consulting Services Agreement")
        .put("provider", "Acme Consulting LLC")
        .put("client", "Globex Corporation")
        .put("effectiveDate", "2026-09-13")
        .put("recital", "The Provider agrees to deliver consulting services to the Client.")
        .build();

byte[] pdf = new ContractGenerator().generate("contract", data);
Files.write(Path.of("contract.pdf"), pdf);
```

Optionally assert that specific fields exist in the output:

```java
byte[] pdf = new ContractGenerator()
        .generate("contract", data, Set.of("party.name", "sig.date"));
```

Optionally also mark some of those fields as required (sets the AcroForm Required flag, which
DocuSign auto-detection carries over to the resulting tab — see "DocuSign hand-off" below):

```java
byte[] pdf = new ContractGenerator().generate(
        "contract", data,
        Set.of("party.name", "sig.date"),   // expected fields
        Set.of("sig.date"));                // required fields
```

## Reading filled values

Once a user has filled in the generated PDF (in Acrobat, a browser, or after a round trip through
a signing service), read the values back by field name — the inverse of `generate`:

```java
Map<String, String> values = new ContractGenerator().readValues(Path.of("filled-contract.pdf"));
// values.get("party.name") -> "Jane Doe"
// values.get("agree.terms") -> "true"
```

`readValues` also accepts `byte[]` or an `InputStream` (the stream is read but not closed).
`form.ContractFormReader` implements this directly if you don't need the rest of the facade.

Value conventions:

- Text / textarea / choice fields return the typed value, or `""` if unfilled.
- Checkboxes are normalized to `"true"` / `"false"`.
- Only **terminal** fields are returned — dotted-name parents (e.g. `party` for `party.name`)
  are internal AcroForm hierarchy nodes, not values, and are skipped.
- A PDF with no AcroForm at all (a plain, non-fillable PDF) returns an **empty map** rather than
  throwing.

## Pipeline

```
ContractData ─▶ Thymeleaf (ContractTemplateEngine) ─▶ XHTML
             ─▶ openhtmltopdf (HtmlToPdfRenderer, form controls) ─▶ PDF + AcroForm
             ─▶ PDFBox (AcroFormPostProcessor: validate, NeedAppearances) ─▶ byte[]

byte[] (filled PDF) ─▶ PDFBox (ContractFormReader: walk AcroForm) ─▶ Map<String, String>
```

| Class                             | Responsibility                                                |
|-----------------------------------|---------------------------------------------------------------|
| `ContractGenerator`               | Public facade wiring the generate and read pipelines together |
| `template.ContractTemplateEngine` | Thymeleaf → XHTML (XML template mode)                         |
| `render.HtmlToPdfRenderer`        | openhtmltopdf render; registers the bundled font              |
| `form.FieldNaming`                | AcroForm field-name convention + validation                   |
| `form.AcroFormPostProcessor`      | Validate fields, set `NeedAppearances`, reject dupes          |
| `form.ContractFormReader`         | Reads current AcroForm field values from a (filled) PDF       |
| `model.ContractData`              | Typed holder for dynamic template values                      |

## Authoring templates

Templates live in `src/main/resources/templates/*.html` and are rendered in Thymeleaf **XML
mode**, so they must be **well-formed XHTML** (namespaced root, all void elements self-closed:
`<input ... />`, `<meta ... />`, `<br/>`).

Non-obvious rules that will otherwise cause runtime failures:

- **Wrap all form controls in a `<form>` element.** openhtmltopdf silently skips any control that
  has no enclosing `<form>`, producing a PDF with no AcroForm.
- **Give text/textarea controls a font-relative dimension**, e.g. `min-width: 1em`. This forces
  openhtmltopdf to resolve the control's font; without it, rendering throws
  `NullPointerException: ... "spec" is null` inside form-control processing.
- **A font must be registered** because openhtmltopdf ships with no default font. A font is
  bundled at `src/main/resources/fonts/Contract-Regular.ttf` (Open Sans, SIL OFL) and registered
  under the family `ContractFont` (`HtmlToPdfRenderer.FONT_FAMILY`); templates use that family.
- **The control's `name` attribute becomes the AcroForm field name.** Names must satisfy
  `FieldNaming` (start with a letter; only letters, digits, `.`, `_`, `-`). Dotted names such as
  `party.name` create a hierarchical field (`party` → `name`) whose fully-qualified name is
  `party.name`.

### Control-type support

| HTML control              | AcroForm result                                                                                                                    |
|---------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `<input type="text">`     | text field                                                                                                                         |
| `<textarea>`              | multiline text field                                                                                                               |
| `<input type="checkbox">` | checkbox                                                                                                                           |
| `<input type="radio">`    | radio button                                                                                                                       |
| `<select>`                | choice field                                                                                                                       |
| signature                 | none — openhtmltopdf has no signature-field control; place a named text field and map it to a DocuSign signing tab during hand-off |

> **Checkbox quirk:** openhtmltopdf writes each checkbox's `/Opt` export-value array as a single
> empty string, which corrupts PDFBox's own `PDCheckBox.getValue()` / `isChecked()` (they treat
> the on-state as an index into that array, mapping "checked" to `""`). `ContractFormReader`
> works around this by comparing the field's raw `/V` name directly to its on-appearance state
> instead of relying on those PDFBox convenience methods.

## DocuSign hand-off

Sending a generated PDF to DocuSign for signature is built in (package
`tech.aydin.contracts.generator.docusign`). It uses the official DocuSign eSignature SDK with
**JWT Grant** auth (a service integration; no browser flow) and DocuSign's tab **auto-detection**:
the PDF's AcroForm fields are converted into signing tabs on import, so there's no manual tab
placement or coordinate mapping to maintain.

### One-time DocuSign app setup

1. Create an Integration Key (OAuth client id) in the DocuSign Admin console, of type **JWT**.
2. Generate an RSA keypair for it; keep the private key (PEM) — DocuSign only stores the public key.
3. Grant consent once for the impersonated user by visiting (demo environment):
   `https://account-d.docusign.com/oauth/auth?response_type=code&scope=signature%20impersonation&client_id=<integration_key>&redirect_uri=<any_registered_uri>`
4. Note the account id (GUID) and the impersonated user id (GUID) from the DocuSign Admin console.

### Sending an envelope

```java
DocuSignConfig config = new DocuSignConfig(
        accountId,                    // DocuSign account id (GUID)
        "account-d.docusign.com",     // OAuth host: demo; use "account.docusign.com" for production
        integrationKey,               // JWT integration key (OAuth client id)
        userId,                       // impersonated user's GUID (must have granted consent)
        Files.readAllBytes(Path.of("private_key.pem")));

byte[] pdf = new ContractGenerator().generate(
        "contract", data,
        Set.of("party.name", "party.email", "sig.name", "sig.date"),  // expected fields
        Set.of("party.name", "sig.name", "sig.date"));                // required fields

String envelopeId = DocuSignSender.forLiveDocuSign(config).sendForSignature(new SendRequest(
        "Consulting Services Agreement",
        pdf,
        "Please sign: Consulting Services Agreement",
        List.of(new Signer("Jane Doe", "jane@example.com"))));
```

### Required fields

`ContractGenerator.generate(templateName, data, expectedFieldNames, requiredFieldNames)` marks the
given fields' AcroForm **Required** flag. Combined with the document's `transformPdfFields=true`
setting (always applied by `EnvelopeFactory`), DocuSign's auto-converted tabs inherit the required
attribute — so `requiredFieldNames` controls "must be filled before signing" in both the PDF and
DocuSign, in one place.

### Testing without DocuSign credentials

`EnvelopeFactory` (builds the `EnvelopeDefinition`) and `DocuSignSender` (with a fake
`DocuSignClient`) are unit-tested with no network calls. `JwtAuthenticator` and
`EsignDocuSignClient` do real network I/O and are exercised only by
`DocuSignLiveIntegrationTest`, which is skipped unless `DOCUSIGN_LIVE_TEST=true` and the
corresponding `DOCUSIGN_*` environment variables are set (account id, integration key, user id,
private key path, test signer name/email) — see the class Javadoc for the full list. Run it with:

```bash
DOCUSIGN_LIVE_TEST=true \
DOCUSIGN_ACCOUNT_ID=... DOCUSIGN_INTEGRATION_KEY=... DOCUSIGN_USER_ID=... \
DOCUSIGN_PRIVATE_KEY_PATH=./private_key.pem \
DOCUSIGN_SIGNER_NAME="Jane Doe" DOCUSIGN_SIGNER_EMAIL=jane@example.com \
./mvnw -Dtest=DocuSignLiveIntegrationTest test
```

### Scope & caveats

- Only JWT Grant auth is supported (no authorization-code / implicit flows).
- Tabs are auto-detected from the AcroForm; there's no explicit per-tab placement API.
- With multiple signers, auto-detected tabs are all assigned to the first signer unless field
  names encode the intended recipient. Single-signer envelopes are the well-supported path.
- Out of scope: embedded signing (recipient views), envelope status polling.

## Receiving signed documents (Connect webhook)

Once an envelope is completed, [DocuSign Connect](https://developers.docusign.com/platform/webhooks/connect/)
can POST a notification to your endpoint. When the Connect configuration has **"Include Documents"**
enabled, that notification carries the signed PDF(s) inline. `ConnectWebhookParser` turns the raw
notification body back into the field values — the inverse of the send pipeline.

This library provides only the parsing method; wiring it to an HTTP route is up to you.

```java
// rawBody: the exact request body bytes your endpoint received.
SignedEnvelope envelope = new ConnectWebhookParser().parse(rawBody);

if (envelope.isCompleted()) {
    Map<String, String> values = envelope.fields();   // merged across the envelope's documents
    // values.get("party.name") -> "Jane Doe"
    // values.get("agree.terms") -> "true"

    for (SignedDocument doc : envelope.documents()) {
        // doc.name(), doc.type() ("content" / "summary"), doc.pdfBytes(), doc.fields()
    }
}
```

`SignedEnvelope` exposes the `envelopeId`, `status`, and the list of `SignedDocument`s (each with
its name, type, decoded `pdfBytes`, and parsed `fields`). Parsing is **status-agnostic**: a
notification for a not-yet-completed envelope (or one sent without documents) parses successfully
into an empty documents list rather than throwing — inspect `status()` / `isCompleted()` to decide
what to do. The completion-certificate ("summary") document has no form and parses to empty fields.

### Verifying the HMAC signature

If you enable HMAC security on the Connect configuration, DocuSign signs each request. Verify it
against the **raw** request body before parsing:

```java
ConnectHmacVerifier verifier = new ConnectHmacVerifier();
String signature = request.getHeader("X-DocuSign-Signature-1");   // may be -1, -2, ... per key
if (!verifier.verify(rawBody, signature, hmacSecret)) {
    // reject the request (401)
}
// verifier.verifyAny(rawBody, List.of(sig1, sig2), hmacSecret) checks multiple keys
```

The comparison is constant-time. Always verify the bytes exactly as received — re-serializing the
parsed JSON changes the bytes and breaks verification.

### Scope & caveats (webhook)

- Only the **JSON** eSignature Connect payload format is parsed (legacy XML
  `DocuSignEnvelopeInformation` is not supported).
- Documents are only present when Connect has "Include Documents" enabled; otherwise the parser
  returns an empty documents list. Fetching them from the API instead is out of scope.

## Running as a Temporal worker (temporal-worker-k8s rock)

The generator can run as a [Temporal](https://temporal.io/) worker packaged as an OCI
[rock](https://documentation.ubuntu.com/rockcraft/), suitable as the workload (`oci-image`)
resource for the [`temporal-worker-k8s`](https://charmhub.io/temporal-worker-k8s?channel=2.0/stable)
charm. The worker registers `ContractSigningWorkflow` on the configured task queue; the workflow
takes a **template name + parameters**, generates the fillable PDF, and sends it to DocuSign,
returning the envelope id.

### Workflow contract

```java
ContractSigningRequest request = new ContractSigningRequest(
        "contract",                                   // template name (templates/contract.html)
        Map.of("title", "Consulting Services Agreement",
               "provider", "Acme Consulting LLC",
               "client", "Globex Corporation",
               "effectiveDate", "2026-09-13",
               "recital", "The Provider agrees to deliver consulting services."),
        Set.of("party.name", "party.email"),          // expected AcroForm fields (optional)
        Set.of("party.name"),                         // required fields -> required DocuSign tabs
        "Consulting Agreement",                       // DocuSign document name
        "Please sign: Consulting Agreement",          // signing-request email subject
        List.of(new SignerInfo("Jane Doe", "jane@example.com")));

// A Temporal client (anywhere) starts the workflow on the worker's task queue:
ContractSigningWorkflow workflow = client.newWorkflowStub(
        ContractSigningWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue(System.getenv("TEMPORAL_QUEUE")).build());
String envelopeId = workflow.generateAndSend(request);
```

The bundled templates travel inside the worker jar, so `templateName` is resolved from the
classpath — no template files need to be mounted.

### Environment variables

Injected by the charm (core connection):

| Variable                   | Purpose                                                  | Default          |
|----------------------------|----------------------------------------------------------|------------------|
| `TEMPORAL_HOST`            | Temporal frontend `host:port`                            | `localhost:7233` |
| `TEMPORAL_NAMESPACE`       | Temporal namespace                                       | `default`        |
| `TEMPORAL_QUEUE`           | task queue to poll (**required**)                        | —                |
| `TEMPORAL_TLS_ROOT_CAS`    | root CA PEM for a TLS connection                         | none (plaintext) |
| `TEMPORAL_PROMETHEUS_PORT` | if set, serves worker metrics at `/metrics` on this port | disabled         |

DocuSign credentials (set via the charm's `environment` config), read by the `sendToDocuSign`
activity to build a `DocuSignConfig`:

| Variable                                                | Purpose                                      |
|---------------------------------------------------------|----------------------------------------------|
| `DOCUSIGN_ACCOUNT_ID`                                   | account GUID                                 |
| `DOCUSIGN_OAUTH_BASE_PATH`                              | OAuth host, e.g. `account-d.docusign.com`    |
| `DOCUSIGN_INTEGRATION_KEY`                              | integration key (OAuth client id)            |
| `DOCUSIGN_USER_ID`                                      | impersonated user GUID                       |
| `DOCUSIGN_PRIVATE_KEY` *or* `DOCUSIGN_PRIVATE_KEY_PATH` | RSA private key PEM (inline) or a path to it |

### Building and using the rock

```bash
rockcraft pack                 # produces contract-generator-worker_0.1.0_amd64.rock
# upload the rock to your registry / import it, then attach it as the charm resource:
juju deploy temporal-worker-k8s --channel 2.0/stable
juju attach-resource temporal-worker-k8s oci-image=<your-registry>/contract-generator-worker:0.1.0
```

The rock is built on `ubuntu@26.04` (OpenJDK 25 via apt) and provides
`/app/scripts/start-worker.sh`, which runs the executable worker uber-jar
(`./mvnw package` attaches it as `contract-generator-*-worker.jar`).

### Scope & caveats (worker)

- Implements the **core** Temporal connection (host/namespace/queue/TLS) plus optional Prometheus
  metrics.
- The generated PDF passes between the two activities through workflow history (base64); this is
  fine for typical contracts but note Temporal's ~2 MB payload limit for very large documents.

## License

This project is licensed under the Apache License, Version 2.0. See the
[LICENSE](LICENSE) file for the full text.

Copyright 2026 Emre Aydin

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this project except in compliance with the License. You may obtain a copy of the
License at <https://www.apache.org/licenses/LICENSE-2.0>.
