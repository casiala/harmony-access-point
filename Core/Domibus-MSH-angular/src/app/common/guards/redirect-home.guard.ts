import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot} from '@angular/router';
import {SecurityService} from '../../security/security.service';

/**
 * It will redirect to home ('/') if user is authenticated
 * @author Catalin Enache
 * @since 4.1
 */
@Injectable()
export class RedirectHomeGuard {

  constructor(private router: Router, private securityService: SecurityService) {
  }

  async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    await this.securityService.isAppInitialized();

    const currentUser = this.securityService.getCurrentUser();
    if (!!currentUser) {
      this.router.navigate(['/']);
      return false;
    }
    return true;
  }
}
