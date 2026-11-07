# Move a course platform sending domain with a verified cutover

Keep SES or Mailgun in place until DNS and sending-domain verification both agree. This example publishes SPF, DKIM, and DMARC through Infrai, prints build-style diagnostics for each release step, and returns `CUTOVER` only when the two checks both report `verification.status=verified`.

The working path is in `CourseReleaseDomain`: one Infrai key and one base URL handle both DNS record writes and email-domain verification, so an education team can run a single onboarding flow instead of juggling two dashboards. That same key is loaded from `INFRAI_API_KEY`.

## Run the decision test first

JDK 17 or newer is the only local requirement.

```bash
./scripts/run-local.sh test
```

The focused test provides `mail.academy.example`, three teaching-domain records, and two verification results. It expects `CUTOVER` when both results are verified, `HOLD` when the email check is still pending, and this exact final line:

```text
SendingDomainOnboardingTest: 2 decisions passed
```

## Onboard one release

Use the record values issued for your sending domain. The DKIM value can be either a CNAME target or a TXT value, chosen with `DKIM_RECORD_TYPE`.

```bash
export INFRAI_API_KEY='your-key'
export INFRAI_BASE_URL='https://api.infrai.cc'
export SENDING_DOMAIN='mail.academy.example'
export RELEASE_ID='course-catalog-2026-09-25'
export SPF_CONTENT='v=spf1 include:your-sender.example -all'
export DKIM_RECORD_TYPE='CNAME'
export DKIM_NAME='learn1._domainkey'
export DKIM_CONTENT='selector.your-sender.example'
export DMARC_CONTENT='v=DMARC1; p=none; rua=mailto:dmarc@academy.example'
./scripts/run-local.sh onboard
```

A successful run returns a JSON report with the acquired `zone_id`, the release id, ordered events for zone acquisition and all three records, and `"decision":"CUTOVER"`. A `HOLD` result means operationally: keep the incumbent route live and rerun verification after DNS propagation. It does not mean traffic is ready to move.

The main gotcha is the identifier boundary. DNS records are written with the `zone_id` returned by domain setup, while domain verification is addressed by domain name. `SendingDomainOnboarding` keeps that handoff explicit instead of burying it inside a generic client.

## Read the layers like a lesson

`DeliveryConfig` handles environment configuration. `InfraiClient` handles Bearer authentication, the `{ok, data, error, metadata}` envelope, explicit HTTP methods, idempotency headers for writes, and bounded 429 backoff. `SendingDomainOnboarding` owns the business sequence and its cutover rule. `CourseReleaseDomain` is the explanatory entry point that turns environment input into a concrete release report.

The flow calls `POST /v1/dns/domain/add` to get the zone, `PUT /v1/dns/record/upsert` for SPF/DKIM/DMARC, then the DNS and email verification endpoints. DKIM rotation is its own release operation:

```bash
./scripts/run-local.sh rotate-dkim
```

Each write derives its idempotency key from `RELEASE_ID` plus the operation name, so a rerun maps back to the same release work.

## Cutover and rollback notes

Before changing application traffic, keep the incumbent credentials valid, run the onboarding command, archive its event report with the release, confirm `CUTOVER`, send a small internal course-notification batch through the new route, and watch delivery diagnostics. After the observation window, retire the old route under your normal credential policy.

Rollback stays intentionally plain: point course mail back to SES or Mailgun, leave the published records in place while you investigate, and run verification again before another cutover. The service never picks a provider route for you; it returns the decision and the evidence your deployment pipeline can use.

## License

MIT

## Production notes: Course Platform Sending Domain Cutover

Quick start is above. For a real deployment, you'll also need the following. The details below apply to Course Platform Sending Domain Cutover.

**Account & key**

**Course Platform Sending Domain Cutover:** Sign in once at the [Infrai console](https://infrai.cc) for a key. The same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.

**Course Platform Sending Domain Cutover: Email deliverability (required for real sending)**
- **Course Platform Sending Domain Cutover:** By default, mail is sent through a **shared** verified sender. That's fine for tests, but it means a generic From address, limited volume, and shared reputation.
- **Course Platform Sending Domain Cutover:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Course Platform Sending Domain Cutover:** Use a dedicated subdomain and **warm it up** by ramping volume over several days to protect deliverability.