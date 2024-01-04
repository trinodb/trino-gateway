import React from "react";

interface IErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  info: React.ErrorInfo | null;
}

interface IErrorBoundaryProps {
  children: React.ReactNode;
}

export class ErrorBoundary extends React.Component<IErrorBoundaryProps, IErrorBoundaryState> {
  constructor(props: IErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null, info: null };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    // Update state with error details
    this.setState({ hasError: true, error, info });
  }

  render() {
    if (this.state.hasError) {
      // Render error message
      return (
        <div className="error">
          <h2>Oops, something went wrong!</h2>
          <pre>
            <code>{this.state.error?.toString()}</code>
            <code>{this.state.info?.componentStack}</code>
          </pre>
        </div>
      );
    }
    // if no error occurred, render children
    return this.props.children;
  }
}
