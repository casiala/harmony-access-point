import {Component, Inject, Input, OnInit} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {MessageLogEntry} from 'app/messagelog/support/messagelogentry';
import {AlertService} from 'app/common/alert/alert.service';
import {ConnectionsMonitorService} from '../support/connectionsmonitor.service';
import {PartyResponseRo} from '../../party/support/party';

/**
 * @author Tiago MIGUEL
 * @since 4.0
 *
 * Test service form for a single party.
 */

@Component({
  templateUrl: 'connection-details.component.html',
  styleUrls: ['connection-details.component.css'],
  providers: [ConnectionsMonitorService]
})

export class ConnectionDetailsComponent implements OnInit {

  static readonly MESSAGE_LOG_LAST_TEST_SENT_URL: string = 'rest/messagelog/test/outgoing/latest';
  static readonly MESSAGE_LOG_LAST_TEST_RECEIVED_URL: string = 'rest/messagelog/test/incoming/latest';

  @Input() partyId: string;
  sender: PartyResponseRo;

  messageInfoSent: MessageLogEntry;
  messageInfoReceived: MessageLogEntry;
  isBusy = false;
  private senderPartyId: any;

  constructor(private connectionsMonitorService: ConnectionsMonitorService, private http: HttpClient, private alertService: AlertService,
              public dialogRef: MatDialogRef<ConnectionDetailsComponent>, @Inject(MAT_DIALOG_DATA) public data: any) {
    this.partyId = data.partyId;
    this.senderPartyId = data.senderPartyId;
  }

  async ngOnInit() {
    this.isBusy = true;
    this.sender = null;
    try {
      this.clearInfo();
      await this.getSenderParty();
      if (this.sender && this.partyId) {
        await this.update();
      }
    } catch (err) {
      this.alertService.exception('Error in init.', err)
    } finally {
      this.isBusy = false;
    }
  }

  async test() {
    if (this.isBusy) {
      return;
    }
    this.isBusy = true;
    this.clearInfo();
    try {
      // this will be sent as a param from the main page
      let senderPartyId = this.sender.identifiers[0].partyId;
      this.messageInfoSent.messageId = await this.connectionsMonitorService.sendTestMessage(this.partyId, senderPartyId);
      window.setTimeout(() => {
        this.update();
      }, 1000);
    } catch (err) {
      this.isBusy = false;
      this.alertService.exception('Problems while submitting test', err)
    }
  }

  async update() {
    this.isBusy = true;
    this.alertService.clearAlert();
    try {
      await this.getLastSentRequest(this.senderPartyId, this.partyId);
      if (this.messageInfoSent.messageId) {
        await this.getLastReceivedRequest(this.senderPartyId, this.partyId, this.messageInfoSent.messageId);
      }
    } catch (e) {
      this.alertService.exception('Exception while calling update operation.', e);
    } finally {
      this.isBusy = false;
    }
  }

  private clearInfo() {
    this.messageInfoSent = new MessageLogEntry('', '', '', '', '', '', '', '', '', '', '', null, null, false, null, '', 0);
    this.messageInfoReceived = new MessageLogEntry('', '', '', '', '', '', '', '', '', '', '', null, null, false, null, '', 0);
  }

  async getSenderParty() {
    this.isBusy = true;
    try {
      this.sender = await this.connectionsMonitorService.getSenderParty();
    } catch (error) {
      this.sender = null;
      this.alertService.exception('The test service is not properly configured.', error);
    } finally {
      this.isBusy = false;
    }
  }

  async getLastSentRequest(senderPartyId: string, partyId: string) {
    this.isBusy = true;
    try {
      let searchParams = new HttpParams();
      searchParams = searchParams.append('senderPartyId', senderPartyId);
      searchParams = searchParams.append('partyId', partyId);

      let result = await this.http.get<any>(ConnectionDetailsComponent.MESSAGE_LOG_LAST_TEST_SENT_URL, {params: searchParams}).toPromise();
      if (result) {
        this.alertService.clearAlert();
        this.messageInfoSent.toPartyId = result.partyId;
        this.messageInfoSent.finalRecipient = result.accessPoint;
        this.messageInfoSent.receivedTo = new Date(result.timeReceived);
        this.messageInfoSent.messageId = result.messageId;
      }
    } catch (err) {
      this.alertService.exception(`Error retrieving Last Sent Test Message for ${partyId}`, err);
    } finally {
      this.isBusy = false;
    }
  }

  async getLastReceivedRequest(senderPartyId: string, partyId: string, userMessageId: string) {
    this.isBusy = true;
    try {
      let searchParams = new HttpParams();
      searchParams = searchParams.append('senderPartyId', senderPartyId);
      searchParams = searchParams.append('partyId', partyId);
      searchParams = searchParams.append('userMessageId', userMessageId);
      let result = await this.http.get<any>(ConnectionDetailsComponent.MESSAGE_LOG_LAST_TEST_RECEIVED_URL, {params: searchParams}).toPromise();

      if (result) {
        this.messageInfoReceived.fromPartyId = partyId;
        this.messageInfoReceived.originalSender = result.accessPoint;
        this.messageInfoReceived.receivedFrom = new Date(result.timeReceived);
        this.messageInfoReceived.messageId = result.messageId;
      }
    } catch (err) {
      this.alertService.exception(`Error retrieving Last Received Test Message for ${partyId}`, err);
    } finally {
      this.isBusy = false;
    }
  }

  onUpdateClick() {
    if (this.isBusy) {
      return;
    }
    this.update();
  }
}
