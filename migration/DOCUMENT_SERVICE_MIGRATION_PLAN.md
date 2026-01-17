# Document Service Migration Plan
## From Resume/Job Spec Parsing to Generic Document Parsing with Apache Tika

### Overview
Migrate `document-service` from domain-specific resume/job spec parsing (using Textkernel API) to a generic document parsing service that:
- Uses Apache Tika to extract metadata and text from any document type
- Saves original documents to S3
- Saves JSON representation (metadata + extracted text) to DynamoDB

### Current State Analysis

#### Existing Components
- **Domain Layer**: `DocumentService`, `SourceDocumentService`
- **Infrastructure Layer**: 
  - Persistence: `ParsedResumeRepository`, `ResumeRecord`, `SourceDocumentUploadRepository`
  - Mappers: `TextkernelResumeMapper`, `ResumeResponseMapper`, `ResumeJsonExtractor`
  - Metrics: `TextkernelMetricsRecorder`, `DocumentMetricsRecorder`
- **Presentation Layer**: `ResumeResource` (REST endpoints)
- **DTOs**: `ResumeResponse`, `ParseResumeResponseWrapper`
- **S3**: Bucket `bravo-candidate-resumes`
- **DynamoDB**: Table `RESUMES` with indexes `ActorIdIndex`, `NameIndex`

#### Issues to Address
1. `DocumentService` references `parserService.parseResume()` but dependency is missing (likely partially deleted)
2. Job spec references still exist (`JobSpecRecord`, `JobSpecResourceIT`, `ParseJobResponseWrapper`)
3. Textkernel SDK dependencies need removal
4. Domain-specific naming (resume, actorId) needs generalization

---

## Migration Plan

### Phase 1: Dependency Management

#### 1.1 Remove Textkernel Dependencies
- [ ] Remove `textkernel.sdk.version` property from root `pom.xml`
- [ ] Remove any textkernel SDK dependencies from `document-service/pom.xml`
- [ ] Remove `TextkernelMetricsRecorder` (we already have a `DocumentMetricsRecorder` at service level)
- [ ] Remove `TextkernelResumeMapper` (will be replaced with Tika-based mapper)

#### 1.2 Add Apache Tika Dependency
- [ ] Add Apache Tika dependency to `document-service/pom.xml`:
  ```xml
  <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-core</artifactId>
      <version>3.2.3</version>
  </dependency>
  <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-parsers-standard-package</artifactId>
      <version>3.2.3</version>
  </dependency>
  ```
- [ ] Add version property to root `pom.xml`: `<tika.version>3.2.3</tika.version>`

---

### Phase 2: Domain Model Refactoring

#### 2.1 Rename Resume → Document
- [ ] Rename `ResumeRecord` → `DocumentRecord`
- [ ] Rename `ParsedResumeRepository` → `DocumentRepository`
- [ ] Rename `ResumeResponse` → `DocumentResponse` (in `domain-dtos`)
- [ ] Rename `ResumeResponseMapper` → `DocumentResponseMapper`
- [ ] Rename `ResumeJsonExtractor` → `DocumentJsonExtractor`
- [ ] Update all references throughout codebase

#### 2.2 Update DocumentRecord Structure
- [ ] Update `DocumentRecord` fields:
  - Keep: `transactionId` (partition key), `actorId` (GSI partition key), `parseTimestamp` (GSI sort key)
  - Rename: `resumeUploadKey` → `documentUploadKey`
  - Remove: `emailAddress`, `formattedName` (extract from metadata JSON if needed)
  - Split storage: `metadata` (JSON string of Tika metadata) and `extractedText` (plain text) - separate fields for better search-service integration
  - Add: `documentType` (MIME type from Tika), `fileName` (original filename)
  - Add: `documentSize` (file size in bytes)

#### 2.3 Create Generic Document DTOs
- [ ] Create `DocumentResponse` DTO (replace `ResumeResponse`):
  ```java
  public record DocumentResponse(
      boolean success,
      String transactionId,
      String actorId,
      String documentUploadKey,
      String documentType,
      String fileName,
      Long documentSize,
      Instant parseTimestamp,
      String metadata,  // JSON string of Tika metadata (separate from text for search optimization)
      String extractedText,  // Plain text extracted by Tika (separate from metadata for search optimization)
      String errorMessage
  ) implements MetricsResultIndicator
  ```
- [ ] Remove `ParseResumeResponseWrapper` and `ParseJobResponseWrapper`
- [ ] Create `DocumentParseResult` DTO for internal use:
  ```java
  public record DocumentParseResult(
      String documentType,
      String fileName,
      Long documentSize,
      Metadata metadata,  // Tika Metadata object
      String extractedText
  )
  ```

---

### Phase 3: Infrastructure Layer Updates

#### 3.1 Create Tika Document Parser
- [ ] Create `TikaDocumentParser` service:
  - Use `AutoDetectParser` for automatic format detection
  - Extract metadata using `Metadata` object
  - Extract plain text using `BodyContentHandler`
  - Handle exceptions gracefully
  - Return `DocumentParseResult`

#### 3.2 Update S3 Repository
- [ ] Rename `SourceDocumentUploadRepository.uploadResume()` → `uploadDocument()`
- [ ] Update bucket name: `bravo-candidate-resumes` → `forge-documents` (or keep generic name)
- [ ] Update S3 key structure: `{actorId}-{fileName}` (keep same pattern)
- [ ] Update `SourceDocumentUploadResult` if needed

#### 3.3 Update DynamoDB Repository
- [ ] Rename `ParsedResumeRepository` → `DocumentRepository`
- [ ] Update table name: `RESUMES` → `DOCUMENTS`
- [ ] **Fresh install approach**: Don't check if table exists, just create/recreate it
- [ ] Update index names if needed (or keep `ActorIdIndex` for actor-based queries)
- [ ] Update method names: `findByActorId()`, `save()`, etc.
- [ ] Remove `findByFormattedName()` if not needed for generic documents

#### 3.4 Create Document Mapper
- [ ] Create `TikaDocumentMapper` (replace `TextkernelResumeMapper`):
  - Convert `DocumentParseResult` → `DocumentRecord`
  - Serialize Tika `Metadata` to JSON string (store in `metadata` field)
  - Store extracted text separately in `extractedText` field (not combined with metadata)
  - Generate transaction ID (UUID)
  - Map document type, filename, size
  - **Design for search-service**: Separate metadata and text fields enable efficient indexing

#### 3.5 Update Response Mapper
- [ ] Update `DocumentResponseMapper` (renamed from `ResumeResponseMapper`):
  - Convert `DocumentRecord` → `DocumentResponse`
  - Map `metadata` field directly (already JSON string)
  - Map `extractedText` field directly (already plain text)
  - Remove `ResumeJsonExtractor` dependency (no longer needed with separate fields)

#### 3.6 Update Metrics
- [ ] Rename `TextkernelMetricsRecorder` → `DocumentMetricsRecorder` (or merge into existing)
- [ ] Update metric names: `textkernel.api` → `document.parse`
- [ ] Update operation tags to generic names

---

### Phase 4: Domain Layer Updates

#### 4.1 Update DocumentService
- [ ] Remove `parserService` dependency (Textkernel)
- [ ] Inject `TikaDocumentParser`
- [ ] Update method signatures:
  - `uploadAndParseResume()` → `uploadAndParseDocument()`
  - `getResume()` → `getDocument()`
- [ ] Update implementation:
  - Upload to S3 via `sourceDocumentService.uploadDocument()`
  - Parse with `tikaDocumentParser.parse()`
  - Map to record with `tikaDocumentMapper.toRecord()`
  - Save to DynamoDB
  - Return `DocumentResponse`
- [ ] Update cache name: `parsed-resumes` → `parsed-documents`

#### 4.2 Update SourceDocumentService
- [ ] Rename `uploadResume()` → `uploadDocument()`
- [ ] Update parameter names and documentation

---

### Phase 5: Presentation Layer Updates

#### 5.1 Update REST Resource
- [ ] Rename `ResumeResource` → `DocumentResource`
- [ ] Update path: `/resumes` → `/documents`
- [ ] Update endpoints:
  - `POST /documents` (create/upload document) - accepts `@RestForm("documents") final List<FileUpload> documents`
  - `GET /documents?actorId={actorId}` (get document by actor)
- [ ] Update method names and parameter names
- [ ] Update `@AllowedServices` annotations if needed
- [ ] Support multiple document uploads via `List<FileUpload>` interface

#### 5.2 Update REST Clients
- [ ] Update `ParseServiceClient` (in `domain-clients`):
  - Rename `createResume()` → `createDocument()`
  - Update path: `/resumes` → `/documents`
  - Remove `createJobSpec()` method
- [ ] Update `DocumentServiceClient`:
  - Rename `getResume()` → `getDocument()`
  - Update method signatures

---

### Phase 6: Cleanup

#### 6.1 Remove Job Spec References
- [ ] Delete `JobSpecRecord.java`
- [ ] Delete `JobSpecResourceIT.java`
- [ ] Delete `ParseJobResponseWrapper.java`
- [ ] Delete `JobSpecResponse.java` (in `domain-dtos`)
- [ ] Delete `JobSpec.java` (in `domain-dtos/textkernel`)
- [ ] Remove job spec references from health checks
- [ ] Remove `JOBS` table references

#### 6.2 Remove Resume-Specific DTOs
- [ ] Delete `Resume.java` (in `domain-dtos/textkernel`)
- [ ] Update or remove `ResumeResponse` (replaced by `DocumentResponse`)

#### 6.3 Update Health Checks
- [ ] Update `DocumentServiceHealthChecks`:
  - Remove `bravo-client-jobs` bucket check
  - Remove `JOBS` table check
  - Update bucket name: `bravo-candidate-resumes` → `forge-documents`
  - Update table name: `RESUMES` → `DOCUMENTS`

#### 6.4 Update Tests
- [ ] Rename `ResumeResourceIT` → `DocumentResourceIT`
- [ ] Update test data and assertions
- [ ] Update mock table names
- [ ] Update S3 bucket names in tests
- [ ] Remove `JobSpecResourceIT`

---

### Phase 7: Database Migration

#### 7.1 DynamoDB Table
- [ ] Create `DOCUMENTS` table (fresh install - blow away existing `RESUMES` table if it exists)
- [ ] **No existence checks**: Just create/recreate the table
- [ ] Update table schema:
  - Partition key: `transactionId` (String)
  - GSI: `ActorIdIndex` (partition: `actorId`, sort: `parseTimestamp`)
  - Attributes: `documentUploadKey`, `documentType`, `fileName`, `documentSize`, `metadata` (JSON), `extractedText` (String), `parseTimestamp`
- [ ] No need to migrate any existing data, tables are empty / in development only.

#### 7.2 S3 Bucket
- [ ] Create `forge-documents` bucket (fresh install - blow away existing `bravo-candidate-resumes` bucket if it exists)
- [ ] **No existence checks**: Just create/recreate the bucket
- [ ] Update bucket policies and IAM permissions if needed

---

### Phase 8: Integration Updates

#### 8.1 Update Backend Applications
- [ ] Update `backend-candidate` application:
  - Update `ResumeController` → `DocumentController`
  - Update path: `/resumes` → `/documents`
  - Delete `ParseServiceClient` and add a #create POST method to the `DocumentServiceClient` 

#### 8.2 Update Other Services
- [ ] Search for all references to `ResumeResponse`, `getResume()`, `createResume()`
- [ ] Update `actor-service` if it calls document-service
- [ ] Update any other consumers of document-service API

---

## Implementation Order

### Recommended Sequence
1. **Phase 1**: Dependency management (remove Textkernel, add Tika)
2. **Phase 2**: Domain model refactoring (rename, create DTOs)
3. **Phase 3.1-3.2**: Create Tika parser and update S3 (can test independently)
4. **Phase 3.3-3.6**: Update repositories, mappers, metrics
5. **Phase 4**: Update domain services
6. **Phase 5**: Update presentation layer
7. **Phase 6**: Cleanup job spec and old references
8. **Phase 7**: Database migration (can be done in parallel with code changes)
9. **Phase 8**: Integration updates

---

## Testing Strategy

### Unit Tests
- [ ] Test `TikaDocumentParser` with various document types (PDF, DOCX, TXT, etc.)
- [ ] Test `TikaDocumentMapper` conversion logic
- [ ] Test `DocumentRepository` CRUD operations
- [ ] Test `SourceDocumentUploadRepository` S3 uploads

### Integration Tests
- [ ] Test `DocumentResource` endpoints
- [ ] Test end-to-end flow: upload → parse → save → retrieve
- [ ] Test error handling (invalid files, parsing failures)

### Manual Testing
- [ ] Test with various document types (PDF, Word, Excel, images, etc.)
- [ ] Verify S3 uploads
- [ ] Verify DynamoDB persistence
- [ ] Verify separate `metadata` and `extractedText` fields are stored correctly
- [ ] Verify JSON structure is optimized for future search-service integration

---

## Rollback Plan

There is no need for a rollback plan, we fix forward.

---

## Open Questions

1. **Actor ID**: Should we keep `actorId` as the identifier, or make it more generic (e.g., `ownerId`, `userId`)? Keep actorId
2. **Table Migration**: Should we migrate existing `RESUMES` data or start fresh with `DOCUMENTS`? Rename existing tables, no need for data migration or updates, we can blow away existing tables and recreate - so don't add V2, just update V1 as baseline.
3. **Bucket Strategy**: New bucket or repurpose existing? Rename existing, again no need for data migration.
4. **Document Types**: Should we restrict allowed document types or accept any Tika-supported format? Any tika supported is fine but let's keep our interface of `@RestForm("documents") final List<FileUpload> documents`
5. **Metadata Structure**: What specific metadata fields should we extract/store beyond Tika's default? Default tika is fine for now.
6. **Search Service**: Should we design the JSON structure with future search-service in mind? Yes.

---

## Estimated Effort

- **Phase 1-2**: 2-3 hours (dependency management, renaming)
- **Phase 3**: 4-6 hours (Tika integration, repositories, mappers)
- **Phase 4-5**: 2-3 hours (domain and presentation updates)
- **Phase 6**: 1-2 hours (cleanup)
- **Phase 7**: 2-4 hours (database migration, testing)
- **Phase 8**: 2-3 hours (integration updates)
- **Testing**: 3-4 hours
- **Total**: ~16-25 hours

---

## Notes

- Apache Tika version 3.2.3 is the latest stable version as of 2026, see https://tika.apache.org/3.2.3/gettingstarted.html
- Tika supports 1000+ file formats automatically
- Tika metadata includes: content type, creation date, author, title, etc.
- Consider adding document validation (size limits, type restrictions) if needed
- Consider adding document versioning if same document is uploaded multiple times
