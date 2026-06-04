declare module 'pagedjs' {
  export class Previewer {
    preview(
      content: string | HTMLElement,
      stylesheets: Array<string>,
      target: HTMLElement,
    ): Promise<unknown>;
  }
}
