import {ChangeDetectorRef, Component, OnInit} from '@angular/core';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/catch';
import {TrustStoreService} from './support/trustore.service';
import {AlertService} from '../common/alert/alert.service';
import {HttpClient} from '@angular/common/http';
import {ApplicationContextService} from '../common/application-context.service';
import {ComponentName} from '../common/component-name-decorator';
import {BaseTruststoreComponent} from './base-truststore.component';
import {FileUploadValidatorService} from '../common/file-upload-validator.service';
import {DialogsService} from '../common/dialogs/dialogs.service';

@Component({
  selector: 'app-keystore',
  templateUrl: './base-truststore.component.html',
  providers: [TrustStoreService]
})
@ComponentName('KeyStore')
export class KeystoreComponent extends BaseTruststoreComponent implements OnInit {

  constructor(applicationService: ApplicationContextService, http: HttpClient, trustStoreService: TrustStoreService,
              alertService: AlertService, changeDetector: ChangeDetectorRef,
              fileUploadValidatorService: FileUploadValidatorService, dialogsService: DialogsService) {
    super(applicationService, http, trustStoreService, alertService, changeDetector, fileUploadValidatorService, trustStoreService, dialogsService);

    this.BASE_URL = 'rest/keystore';
    this.CSV_URL = this.BASE_URL + '/csv';
    this.DOWNLOAD_URL = this.BASE_URL + '/download';
    this.UPLOAD_URL = this.BASE_URL + '/save';
    this.LIST_ENTRIES_URL = this.BASE_URL + '/list';

    this.showResetOperation = true;
  }

  ngOnInit(): void {
    super.ngOnInit();

    this.checkModifiedOnDisk();
  }
}
