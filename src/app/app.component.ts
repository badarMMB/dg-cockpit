import { Component, OnInit, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NotificationService } from './services/notification.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private notifications = inject(NotificationService);

  ngOnInit() {
    this.notifications.connect();
  }
}
