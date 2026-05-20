package no.nav.sokos.skattekort.utils

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.ValidationReport
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

object ApiTestUtils {
    suspend fun HttpResponse.validationReport(
        validator: OpenApiInteractionValidator,
        method: HttpMethod,
        path: String,
        content: String,
        contentType: ContentType = ContentType.Application.Json,
    ): ValidationReport =
        validator.validate(
            SimpleRequest
                .Builder(method.value, path)
                .withHeader(HttpHeaders.ContentType, contentType.toString())
                .withHeader(HttpHeaders.Authorization, "Bearer test")
                .withBody(content)
                .build(),
            SimpleResponse
                .Builder(this.status.value)
                .withBody(this.bodyAsText())
                .build(),
        )
}
