import { useLayoutEffect } from 'react';
import { useLocation } from 'react-router-dom';

export default function RouteScrollReset() {
  const { pathname } = useLocation();

  useLayoutEffect(() => {
    // Reset before the new route is painted. Assigning scrollTop directly keeps
    // the reset immediate even though the site enables smooth scrolling in CSS.
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
  }, [pathname]);

  return null;
}
