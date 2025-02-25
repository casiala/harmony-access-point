import {Constructable} from './base-list.component';
import {RowLimiterBase} from '../row-limiter/row-limiter-base';
import {IPageableList, PaginationType} from './ipageable-list';
import {instanceOfFilterableList} from './type.utils';
import {OnInit} from '@angular/core';
import {HttpParams} from '@angular/common/http';
import {SecurityService} from '../../security/security.service';

/**
 * @author Ion Perpegel
 * @since 4.2
 *
 * A mixin for components that display a list of items that are paged on server
 */
export let ServerPageableListMixin = (superclass: Constructable) => class extends PageableListMixin(superclass) {
  constructor(...args) {
    super(...args);
    this.type = PaginationType.Server;
  }

  // when server-paging, call get data from server
  public async page() {
    await this.loadServerData();
  }

  protected createAndSetParameters(): HttpParams {
    let params = super.createAndSetParameters();

    params = params.append('page', this.offset.toString());
    params = params.append('pageSize', this.rowLimiter.pageSize.toString());

    return params;
  }

};

/**
 * @author Ion Perpegel
 * @since 4.2
 *
 * A mixin for components that display a list of items that are paged on client
 */
export let ClientPageableListMixin = (superclass: Constructable) => class extends PageableListMixin(superclass) {
  constructor(...args) {
    super(...args);
    this.type = PaginationType.Client;
  }

  getLastPage(): number {
    if (!this.rows || !this.rowLimiter || !this.rowLimiter.pageSize) {
      return 0;
    }
    return Math.floor(this.rows.length / this.rowLimiter.pageSize);
  }

  setPage(offset: number): void {
    this.alertService.clearAlert();
    this.offset = offset;
  }
};

/**
 * @author Ion Perpegel
 * @since 4.2
 *
 * A mixin for components that display a list of items that are paged on server or client
 */
export let PageableListMixin = (superclass: Constructable) => class extends superclass
  implements IPageableList, OnInit {

  public type: PaginationType;
  public offset: number;
  public rowLimiter: RowLimiterBase;

  constructor(...args) {
    super(...args);

    this.offset = 0;
    this.rowLimiter = new RowLimiterBase();
  }

  public ngOnInit(): void {
    if (super.ngOnInit) {
      super.ngOnInit();
    }
  }

  public page() {
  }

  public changePageSize(newPageLimit: number) {
    this.alertService.clearAlert();

    this.offset = 0;
    this.rowLimiter.pageSize = newPageLimit;

    if (instanceOfFilterableList(this)) {
      this.resetFilters();
    }
    this.page();
  }

  public async onPage(event) {
    if (this.isLoading) {
      console.log('exiting onPage due to isLoading')
      return;
    }

    this.alertService.clearAlert();

    await this.loadPage(event.offset);
  }

  public async resetPage() {
    return this.loadPage(0);
  }

  public async loadPage(offset: number) {
    const canChangePage = await this.canProceedToPageChange();
    if (canChangePage) {
      if (instanceOfFilterableList(this)) {
        this.resetFilters();
      }
      super.selected = [];
      this.offset = offset;
      await this.page();
    } else {
      // TODO: try to use before event instead(if exists) or make grid show the correct value
    }
    return canChangePage;
  }

  private async canProceedToPageChange(): Promise<boolean> {

    if (this.type == PaginationType.Client) {
      return true;
    }

    let securityService = this.applicationService.injector.get(SecurityService);
    return securityService.canAbandonUnsavedChanges(this);
  }

  // using an arrow-function instead of a regular function to preserve the correct "this" when called from the row-limiter component context
  onPageSizeChanging = async (newPageLimit: number): Promise<boolean> => {
    const canChangePage = await this.canProceedToPageChange();
    return !canChangePage;
  };

};

