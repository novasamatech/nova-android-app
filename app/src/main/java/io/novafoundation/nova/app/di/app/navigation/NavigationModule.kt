package io.novafoundation.nova.app.di.app.navigation

import dagger.Module
import dagger.Provides
import io.novafoundation.nova.app.root.navigation.NavigationHolder
import io.novafoundation.nova.app.root.navigation.Navigator
import io.novafoundation.nova.app.root.presentation.RootRouter
import io.novafoundation.nova.common.di.scope.ApplicationScope
import io.novafoundation.nova.common.resources.ContextManager
import io.novafoundation.nova.feature_assets.presentation.AssetsRouter
import io.novafoundation.nova.feature_crowdloan_impl.presentation.CrowdloanRouter
import io.novafoundation.nova.feature_onboarding_impl.OnboardingRouter
import io.novafoundation.nova.feature_staking_impl.presentation.StakingRouter
import io.novafoundation.nova.splash.SplashRouter

@Module(
    includes = [
        AccountNavigationModule::class,
        DAppNavigationModule::class,
        NftNavigationModule::class,
        StakingNavigationModule::class,
        LedgerNavigationModule::class,
        CurrencyNavigationModule::class,
        GovernanceNavigationModule::class,
        VoteNavigationModule::class,
        VersionsNavigationModule::class
    ]
)
class NavigationModule {

    @ApplicationScope
    @Provides
    fun provideNavigatorHolder(
        contextManager: ContextManager
    ): NavigationHolder = NavigationHolder(contextManager)

    @ApplicationScope
    @Provides
    fun provideNavigator(
        navigatorHolder: NavigationHolder
    ): Navigator = Navigator(navigatorHolder)

    @Provides
    @ApplicationScope
    fun provideRootRouter(navigator: Navigator): RootRouter = navigator

    @ApplicationScope
    @Provides
    fun provideSplashRouter(navigator: Navigator): SplashRouter = navigator

    @ApplicationScope
    @Provides
    fun provideOnboardingRouter(navigator: Navigator): OnboardingRouter = navigator

    @ApplicationScope
    @Provides
    fun provideAssetsRouter(navigator: Navigator): AssetsRouter = navigator

    @ApplicationScope
    @Provides
    fun provideStakingRouter(navigator: Navigator): StakingRouter = navigator

    @ApplicationScope
    @Provides
    fun provideCrowdloanRouter(navigator: Navigator): CrowdloanRouter = navigator
}
