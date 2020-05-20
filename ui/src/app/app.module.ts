import {Component, Inject, NgModule, OnInit} from '@angular/core';
import {
  LocalStorageService,
  PortofinoModule,
  PortofinoUpstairsModule,
  NOTIFICATION_HANDLERS,
  MatSnackBarNotificationService,
  LOCALE_STORAGE_SERVICE, PortofinoAppComponent
} from "portofino";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatMenuModule } from "@angular/material/menu";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatTreeModule } from "@angular/material/tree";
import {BrowserModule} from "@angular/platform-browser";
import {BrowserAnimationsModule} from "@angular/platform-browser/animations";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {QuillModule} from "ngx-quill";
import {HttpClientModule} from "@angular/common/http";
import {FlexLayoutModule} from "@angular/flex-layout";
import {MatMomentDateModule} from "@angular/material-moment-adapter";
import {FileInputAccessorModule} from "file-input-accessor";
import {TranslateModule} from "@ngx-translate/core";
import {ScrollingModule} from "@angular/cdk/scrolling";
import {NgxdModule} from "@ngxd/core";
import {registerLocaleData} from "@angular/common";
import localeEs from "@angular/common/locales/es";
import localeIt from "@angular/common/locales/it";
import {RouterModule} from "@angular/router";

registerLocaleData(localeIt);
registerLocaleData(localeEs);

@Component({
  selector: 'app-root',
  template: `<portofino-app appTitle="Portofino Upstairs" [preInit]="initApiRoot"></portofino-app>`
})
export class AppComponent {

  constructor(@Inject(LOCALE_STORAGE_SERVICE) protected storage: LocalStorageService) {}

  initApiRoot = (app: PortofinoAppComponent) => {
    const apiRoot = this.storage.get("portofino.upstairs.apiRoot");
    console.log("API root:", apiRoot);
    if(apiRoot) {
      app.apiRoot = apiRoot;
    } else {
      app.apiRoot = "http://localhost:8080/api";
    }
    app.upstairsLink = null;
  };
}

@NgModule({
  declarations: [AppComponent],
  providers: [
    { provide: NAVIGATION_COMPONENT, useFactory: AppModule.navigation },
    { provide: NOTIFICATION_HANDLERS, useClass: MatSnackBarNotificationService, multi: true },
  ],
  imports: [
    RouterModule.forRoot(PortofinoModule.defaultRoutes(), PortofinoModule.defaultRouterConfig()),
    PortofinoModule, PortofinoUpstairsModule,
    BrowserModule, BrowserAnimationsModule, FlexLayoutModule, FormsModule, HttpClientModule, ReactiveFormsModule,
    MatAutocompleteModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatDatepickerModule, MatDialogModule,
    MatDividerModule, MatExpansionModule, MatFormFieldModule, MatIconModule, MatInputModule, MatListModule, MatMenuModule,
    MatPaginatorModule, MatProgressBarModule, MatRadioModule, MatSelectModule, MatSidenavModule, MatSnackBarModule,
    MatSortModule, MatTableModule, MatTreeModule, MatToolbarModule, MatMomentDateModule, ScrollingModule,
    FileInputAccessorModule, NgxdModule, QuillModule,
    TranslateModule.forRoot()],
  entryComponents: [],
  bootstrap: [AppComponent]
})
export class AppModule {
  static navigation() {
    return DefaultNavigationComponent;
  }
}
