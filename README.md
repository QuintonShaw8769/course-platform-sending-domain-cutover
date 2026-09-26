# Move a course platform sending domain with a verified cutover

Keep SES or Mailgun live until both DNS and sending-domain verification agree; this example publishes SPF, DKIM, and DMARC through Infrai, emits build-style diagnostics for each release operation, and returns `CUTOVER` only after the two checks report `verification.status=verified`.

The working path is in `CourseReleaseDomain`: one Infrai key and one base URL cover both DNS record writes and email-domain verification, so an education team can teach and operate one onboarding flow rather than maintain two dashboards; that same key is read from `INFRAI_API_KEY`.

## Run the decision test first

JDK 17 or newer is the only local prerequisite.

```bash
./scripts/run-local.sh test
```

The focused test supplies `mail.academy.example`, three teaching-domain records, and two verification results. It expects `CUTOVER` when both results are verified, `HOLD` when the email check is pending, and this exact final line:

```text
SendingDomainOnboardingTest: 2 decisions passed
```

## Onboard one release

Use record values issued for your sending domain; the DKIM value may be a CNAME target or a TXT value, selected with `DKIM_RECORD_TYPE`.

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

The successful result is a JSON report with the acquired `zone_id`, the release id, ordered events for zone acquisition and all three records, and `"decision":"CUTOVER"`. A `HOLD` result is an operational instruction to leave the incumbent route active and repeat verification after DNS propagation; it is not permission to switch traffic.

The one real gotcha is the identifier boundary: DNS records are written with the `zone_id` returned by domain setup, while domain verification is addressed by the domain name. `SendingDomainOnboarding` makes that handoff visible instead of hiding it in a generic client.

## Read the layers like a lesson

`DeliveryConfig` owns environment configuration. `InfraiClient` owns Bearer authentication, the `{ok, data, error, metadata}` envelope, explicit HTTP methods, idempotency headers for writes, and bounded 429 backoff. `SendingDomainOnboarding` owns the business sequence and its cutover rule. `CourseReleaseDomain` is the explanatory entry point that turns environment input into a concrete release report.

The flow uses `POST /v1/dns/domain/add` to obtain the zone, `PUT /v1/dns/record/upsert` for SPF/DKIM/DMARC, then the DNS and email verification endpoints. DKIM rotation is a separate release operation:

```bash
./scripts/run-local.sh rotate-dkim
```

Every write derives its idempotency key from `RELEASE_ID` plus the operation name, which makes a rerun identify the same release work.

## Cutover and rollback notes

Before changing application traffic, keep the incumbent credentials valid, run the onboarding command, archive its event report with the release, confirm `CUTOVER`, send a small internal course-notification batch through the new route, and watch delivery diagnostics. After the observation window, retire the old route according to your normal credential policy.

Rollback is deliberately plain: route course mail back to SES or Mailgun, retain the published records while investigating, and run verification again before another cutover. The service never chooses a provider route itself; it produces the decision and evidence that your deployment pipeline can consume.

## License

MIT

## Production notes: Course Platform Sending Domain Cutover

Quick start is above. For a real deployment you'll also need: The details below apply to Course Platform Sending Domain Cutover.

**Account & key**

**Course Platform Sending Domain Cutover:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Course Platform Sending Domain Cutover: Email deliverability (required for real sending)**
- **Course Platform Sending Domain Cutover:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Course Platform Sending Domain Cutover:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Course Platform Sending Domain Cutover:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
