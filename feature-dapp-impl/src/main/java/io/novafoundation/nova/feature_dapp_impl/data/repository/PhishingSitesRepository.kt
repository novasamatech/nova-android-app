package io.novafoundation.nova.feature_dapp_impl.data.repository

import io.novafoundation.nova.common.utils.Urls
import io.novafoundation.nova.common.utils.retryUntilDone
import io.novafoundation.nova.core_db.dao.PhishingSitesDao
import io.novafoundation.nova.core_db.model.PhishingSiteLocal
import io.novafoundation.nova.feature_dapp_impl.data.network.phishing.PhishingSitesApi

interface PhishingSitesRepository {

    suspend fun syncPhishingSites()

    suspend fun isPhishing(url: String): Boolean
}

class PhishingSitesRepositoryImpl(
    private val phishingSitesDao: PhishingSitesDao,
    private val phishingSitesApi: PhishingSitesApi,
) : PhishingSitesRepository {

    override suspend fun syncPhishingSites() {
        val remotePhishingSites = retryUntilDone { phishingSitesApi.getPhishingSites() }
        val toInsert = remotePhishingSites.deny.map(::PhishingSiteLocal)

        phishingSitesDao.updatePhishingSites(toInsert)
    }

    override suspend fun isPhishing(url: String): Boolean {
        val host = Urls.hostOf(url)
        val hostSuffixes = extractAllPossibleSubDomains(host)

        return phishingSitesDao.isPhishing(hostSuffixes)
    }

    private fun extractAllPossibleSubDomains(host: String): List<String> {
        val separator = "."

        val segments = host.split(separator)

        val suffixes = (2..segments.size).map { suffixLength ->
            segments.takeLast(suffixLength).joinToString(separator = ".")
        }

        return suffixes
    }
}
