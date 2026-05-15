package com.insightai.pdfsummary.repository

import com.insightai.pdfsummary.domain.PdfDocument
import com.insightai.pdfsummary.domain.ProcessingStatus
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * [PdfDocument] 엔티티에 대한 데이터 접근 레이어.
 *
 * Spring Data JPA의 파생 쿼리(메서드 이름 기반)와 커스텀 JPQL 쿼리를 함께 사용한다.
 * 중복 업로드 감지에는 비관적 쓰기 락을 사용하여 동시 업로드 시 경쟁 조건을 방지한다.
 */
interface PdfDocumentRepository : JpaRepository<PdfDocument, Long> {

    /**
     * 파일 해시로 기존 문서를 조회하면서 비관적 쓰기 락(SELECT FOR UPDATE)을 획득한다.
     *
     * 중복 업로드 처리 시 두 요청이 동시에 같은 파일을 처리하지 못하도록 락을 건다.
     * 락 대기 타임아웃은 3000ms이며 초과 시 [org.springframework.dao.DataAccessException]이 발생한다.
     *
     * @param fileHash SHA-256 해시 (16진수 64자)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT p FROM PdfDocument p WHERE p.fileHash = :fileHash")
    fun findAllByFileHashForUpdate(@Param("fileHash") fileHash: String): List<PdfDocument>

    /**
     * 지정 상태이면서 [before] 시각 이전에 시작된 문서를 조회한다.
     *
     * 스테일 타임아웃 스케줄러([PdfService.recoverStaleProcessing])에서
     * 30분 이상 PROCESSING 상태로 머문 문서를 찾기 위해 사용된다.
     */
    fun findByStatusAndStartedAtBefore(status: ProcessingStatus, before: LocalDateTime): List<PdfDocument>

    /** 생성 시각 내림차순(최신순)으로 전체 목록을 반환한다. */
    fun findAllByOrderByCreatedAtDesc(): List<PdfDocument>
}