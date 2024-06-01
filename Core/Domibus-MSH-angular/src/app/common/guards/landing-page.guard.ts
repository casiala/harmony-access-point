﻿import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot} from '@angular/router';
import {SecurityService} from '../../security/security.service';
import {PropertiesService} from 'app/properties/support/properties.service';

/**
 * Chooses the preferred landing page of already authenticated users based on:
 * - value of the 'domibus.ui.pages.messageLogs.landingPage.enabled' property - if true, the Messages page is the landing page
 * - authorization - either Properties or Error Log page
 */
@Injectable()
export class LandingPageGuard {

  constructor(private router: Router, private securityService: SecurityService,
              private propertiesService: PropertiesService) {
  }

  async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    // make sure the app is properly initialized before checking anything else
    const isInit = await this.securityService.isAppInitialized();
    if (!isInit) {
      return;
    }

    const isAuthenticated = await this.securityService.isAuthenticated();
    if (!isAuthenticated) {
      return false;
    }

    const useMessagesPage = await this.propertiesService.useMessageLogLandingPage();
    if (useMessagesPage) {
      return true;
    }

    if (this.securityService.isCurrentUserAdmin()) {
      return this.router.parseUrl('/properties');
    }

    return this.router.parseUrl('/errorlog');
  }
}
